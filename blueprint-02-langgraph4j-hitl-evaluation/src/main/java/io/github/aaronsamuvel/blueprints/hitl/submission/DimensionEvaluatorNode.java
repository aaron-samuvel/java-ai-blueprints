package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

/** Base class of the three capability evaluators: one rubric, one {@link DimensionScore}. */
abstract class DimensionEvaluatorNode implements NodeAction<ReviewWorkflowState> {

    private final ChatClient chat;
    private final String dimension;
    private final String rubric;

    DimensionEvaluatorNode(ChatClient chat, String dimension, String rubric) {
        this.chat = chat;
        this.dimension = dimension;
        this.rubric = rubric;
    }

    @Override
    public Map<String, Object> apply(ReviewWorkflowState s) {
        DimensionScore raw = chat.prompt()
                .system("TASK: evaluate DIMENSION=" + dimension + "\nYou are a strict reviewer. Rubric: " + rubric
                        + "\nScore 0-100 from evidence in the document only.")
                .user(SubmissionPrompts.profileAndDocument(s))
                .call().entity(DimensionScore.class);
        DimensionScore score = new DimensionScore(dimension, Math.max(0, Math.min(100, raw.score())), raw.rationale());
        return Map.of(ReviewWorkflowState.DIMENSION_SCORES, List.of(score),
                ReviewWorkflowState.CRITIQUES, List.of(dimension + ": " + score.rationale()),
                ReviewWorkflowState.NODE_LOG, List.of(dimension + " on " + (Thread.currentThread().isVirtual() ? "virtual" : "platform") + " thread"));
    }
}
