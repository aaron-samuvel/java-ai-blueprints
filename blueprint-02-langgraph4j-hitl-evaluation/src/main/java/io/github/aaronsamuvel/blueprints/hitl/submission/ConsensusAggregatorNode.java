package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;

/** Joins the four parallel evaluators and applies {@link ConsensusPolicy}. No LLM call. */
public final class ConsensusAggregatorNode implements NodeAction<ReviewWorkflowState> {

    @Override
    public Map<String, Object> apply(ReviewWorkflowState s) {
        ConsensusPolicy.Outcome o = ConsensusPolicy.decide(s.dimensionScores(), s.redFlags());
        return Map.of(ReviewWorkflowState.COMPOSITE_SCORE, o.compositeScore(),
                ReviewWorkflowState.SCORE_SPREAD, o.spread(),
                ReviewWorkflowState.DECISION, o.decision().name(),
                ReviewWorkflowState.DECISION_REASON, o.reason(),
                ReviewWorkflowState.NODE_LOG, List.of("consensusAggregator: " + o.decision() + " (" + o.reason() + ")"));
    }
}
