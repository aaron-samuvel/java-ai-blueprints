package io.github.aaronsamuvel.blueprints.hitl;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import java.util.List;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.eval.EvalCase;
import io.github.aaronsamuvel.blueprints.eval.EvaluationGate;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Multi-agent review graph:
 * <pre>
 * START -> drafter -> evaluator --pass--------------------> human_review (INTERRUPT) --approve--> publish -> END
 *                         |--fail & revision < max--> drafter      |--reject & revision < max--> drafter
 *                         |--fail & revision = max--> human_review |--reject & revision = max--> rejected -> END
 * </pre>
 * The graph pauses before {@code human_review}; a person inspects the checkpointed state, writes
 * their decision into it, and resumes the same thread.
 */
public final class ReviewGraph {

    public static final String DRAFTER = "drafter";
    public static final String EVALUATOR = "evaluator";
    public static final String HUMAN = "human_review";
    public static final String PUBLISH = "publish";
    public static final String REJECTED = "rejected";

    private ReviewGraph() {}

    public static CompiledGraph<ReviewState> build(ChatModel drafterModel, EvaluationGate gate,
                                                   BaseCheckpointSaver saver, int maxRevisions) throws GraphStateException {
        ChatClient drafter = ChatClient.create(drafterModel);

        StateGraph<ReviewState> g = new StateGraph<>(ReviewState.SCHEMA, ReviewState::new)
                .addNode(DRAFTER, node_async(s -> {
                    String user = s.feedback().isEmpty()
                            ? s.question()
                            : s.question() + "\n\nReviewer feedback on your previous draft:\n" + s.feedback()
                              + "\nPrevious draft:\n" + s.draft();
                    String draft = drafter.prompt()
                            .system("Answer ONLY from this context, in one sentence:\n" + s.context())
                            .user(user).call().content();
                    int rev = s.draft().isEmpty() ? 0 : s.revision() + 1;
                    return Map.of(ReviewState.DRAFT, draft, ReviewState.REVISION, rev,
                            ReviewState.HISTORY, List.of("drafter: revision " + rev));
                }))
                .addNode(EVALUATOR, node_async(s -> {
                    EvalCase c = new EvalCase("draft-" + s.revision(), s.question(), List.of(s.context()), s.draft());
                    boolean rel = gate.relevancy(c).isPass();
                    boolean fact = gate.facts(c).isPass();
                    boolean pass = rel && fact;
                    String fb = pass ? "" : "Automatic evaluation failed (" + (rel ? "" : "not relevant ") + (fact ? "" : "not supported by context")
                            + "). Use only facts stated in the context.";
                    return Map.of(ReviewState.EVAL_PASSED, pass, ReviewState.FEEDBACK, fb,
                            ReviewState.HISTORY, List.of("evaluator: " + (pass ? "pass" : "fail")));
                }))
                .addNode(HUMAN, node_async(s -> Map.of(
                        ReviewState.HISTORY, List.of("human: " + s.humanDecision()))))
                .addNode(PUBLISH, node_async(s -> Map.of(ReviewState.STATUS, "PUBLISHED",
                        ReviewState.HISTORY, List.of("published"))))
                .addNode(REJECTED, node_async(s -> Map.of(ReviewState.STATUS, "REJECTED",
                        ReviewState.HISTORY, List.of("rejected"))))
                .addEdge(START, DRAFTER)
                .addEdge(DRAFTER, EVALUATOR)
                .addConditionalEdges(EVALUATOR, edge_async(s ->
                                s.evalPassed() || s.revision() >= maxRevisions ? "review" : "revise"),
                        Map.of("review", HUMAN, "revise", DRAFTER))
                .addConditionalEdges(HUMAN, edge_async(s -> {
                            if ("approve".equals(s.humanDecision())) return "approve";
                            return s.revision() < maxRevisions ? "revise" : "reject";
                        }),
                        Map.of("approve", PUBLISH, "revise", DRAFTER, "reject", REJECTED))
                .addEdge(PUBLISH, END)
                .addEdge(REJECTED, END);

        return g.compile(CompileConfig.builder().checkpointSaver(saver).interruptBefore(HUMAN).build());
    }
}
