package io.github.aaronsamuvel.blueprints.hitl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;

/** Thread-per-review facade over the compiled graph: start, inspect, decide (resume). */
public class ReviewWorkflow {

    /** What a reviewer sees. {@code awaitingHuman} is true while the graph is paused. */
    public record ReviewView(String threadId, String status, boolean awaitingHuman, String draft,
                             int revision, boolean evalPassed, String feedback, List<String> history) {}

    private final CompiledGraph<ReviewState> graph;
    /**
     * LangGraph4j's MemorySaver drops a thread's checkpoints once the graph reaches END, so the
     * final state of finished reviews is kept here (in production: a database table).
     */
    private final Map<String, ReviewView> finished = new ConcurrentHashMap<>();

    public ReviewWorkflow(CompiledGraph<ReviewState> graph) {
        this.graph = graph;
    }

    public ReviewView start(String question, String context) {
        String threadId = UUID.randomUUID().toString();
        graph.invoke(Map.of(ReviewState.QUESTION, question, ReviewState.CONTEXT, context), config(threadId));
        return view(threadId);
    }

    /** Record the human decision on the paused thread and resume it. */
    public ReviewView decide(String threadId, boolean approve, String comment) throws Exception {
        ReviewView current = view(threadId);
        if (!current.awaitingHuman()) throw new IllegalStateException("review " + threadId + " is not awaiting a decision");
        Map<String, Object> update = new HashMap<>();
        update.put(ReviewState.HUMAN_DECISION, approve ? "approve" : "reject");
        if (!approve) update.put(ReviewState.FEEDBACK, "Human reviewer: " + (comment == null ? "" : comment));
        RunnableConfig cfg = graph.updateState(config(threadId), update);
        var last = graph.invoke(GraphInput.resume(), cfg);
        try {
            return view(threadId);
        } catch (IllegalStateException checkpointsReleased) {
            ReviewState s = last.orElseThrow();
            ReviewView done = new ReviewView(threadId, s.status(), false, s.draft(), s.revision(), s.evalPassed(), s.feedback(), s.history());
            finished.put(threadId, done);
            return done;
        }
    }

    public ReviewView view(String threadId) {
        ReviewView done = finished.get(threadId);
        if (done != null) return done;
        var snap = graph.getState(config(threadId));
        ReviewState s = snap.state();
        boolean waiting = ReviewGraph.HUMAN.equals(snap.next());
        String status = waiting ? "AWAITING_HUMAN" : s.status();
        return new ReviewView(threadId, status, waiting, s.draft(), s.revision(), s.evalPassed(), s.feedback(), s.history());
    }

    private static RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }
}
