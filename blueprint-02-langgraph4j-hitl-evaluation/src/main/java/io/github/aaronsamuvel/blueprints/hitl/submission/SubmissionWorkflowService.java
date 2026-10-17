package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;

/** Start, inspect and resume submission-review threads. */
public class SubmissionWorkflowService {

    /** What the API returns for a thread. */
    public record WorkflowView(String threadId, String submissionId, String status, boolean awaitingHuman,
                               String decision, String decisionReason, double compositeScore, int scoreSpread,
                               List<DimensionScore> dimensionScores, List<String> redFlags, List<String> critiques,
                               String parsedProfileJson, HumanVerdict humanVerdict, List<String> nodeLog) {}

    private final CompiledGraph<ReviewWorkflowState> graph;
    private final Executor parallelExecutor;

    public SubmissionWorkflowService(CompiledGraph<ReviewWorkflowState> graph, Executor parallelExecutor) {
        this.graph = graph;
        this.parallelExecutor = parallelExecutor;
    }

    public WorkflowView start(String submissionId, String documentText) {
        if (documentText == null || documentText.isBlank()) throw new IllegalArgumentException("documentText is required");
        String threadId = UUID.randomUUID().toString();
        String id = submissionId == null || submissionId.isBlank() ? "SUB-" + threadId.substring(0, 8) : submissionId;
        graph.invoke(Map.of(ReviewWorkflowState.SUBMISSION_ID, id, ReviewWorkflowState.RAW_DOCUMENT_TEXT, documentText),
                RunnableConfig.builder().threadId(threadId).addParallelNodeExecutor(SubmissionReviewGraph.PARSE, parallelExecutor).build());
        return state(threadId);
    }

    public WorkflowView state(String threadId) {
        var snapshot = graph.stateOf(config(threadId))
                .orElseThrow(() -> new NoSuchElementException("unknown workflow thread " + threadId));
        ReviewWorkflowState s = snapshot.state();
        boolean waiting = SubmissionReviewGraph.HUMAN.equals(snapshot.next());
        return new WorkflowView(threadId, s.submissionId(), waiting ? "AWAITING_HUMAN" : s.status(), waiting,
                s.decision().map(Enum::name).orElse(null), s.decisionReason(), s.compositeScore(), s.scoreSpread(),
                s.dimensionScores(), s.redFlags(), s.critiques(), s.parsedProfileJson(), s.humanVerdict().orElse(null), s.nodeLog());
    }

    public WorkflowView resume(String threadId, HumanVerdict verdict) throws Exception {
        if (!state(threadId).awaitingHuman()) throw new IllegalStateException("workflow " + threadId + " is not awaiting a human decision");
        RunnableConfig updated = graph.updateState(config(threadId), Map.of(ReviewWorkflowState.HUMAN_VERDICT, verdict));
        graph.invoke(GraphInput.resume(), updated);
        return state(threadId);
    }

    private static RunnableConfig config(String threadId) {
        return RunnableConfig.builder().threadId(threadId).build();
    }
}
