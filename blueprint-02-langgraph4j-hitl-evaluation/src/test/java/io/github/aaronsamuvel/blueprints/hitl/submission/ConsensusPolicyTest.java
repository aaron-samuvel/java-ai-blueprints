package io.github.aaronsamuvel.blueprints.hitl.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConsensusPolicyTest {

    private static List<DimensionScore> scores(int skills, int experience, int culture, int audit) {
        return List.of(new DimensionScore(SkillsAndArchitectureNode.DIMENSION, skills, ""),
                new DimensionScore(ExperienceAndResilienceNode.DIMENSION, experience, ""),
                new DimensionScore(CultureAndGovernanceNode.DIMENSION, culture, ""),
                new DimensionScore(RedFlagAuditNode.DIMENSION, audit, ""));
    }

    @Test
    void approvesAtEightyFiveWithNoRedFlags() {
        var o = ConsensusPolicy.decide(scores(85, 90, 80, 100), List.of());
        assertThat(o.compositeScore()).isEqualTo(85.0);
        assertThat(o.decision()).isEqualTo(RoutingDecision.AUTO_APPROVE);
    }

    @Test
    void minorRedFlagBlocksAutoApprovalButNotHumanReview() {
        assertThat(ConsensusPolicy.decide(scores(90, 90, 90, 70), List.of("code sample has no automated tests")).decision())
                .isEqualTo(RoutingDecision.HUMAN_ESCALATION);
    }

    @Test
    void rejectsBelowFifty() {
        var o = ConsensusPolicy.decide(scores(40, 45, 50, 100), List.of());
        assertThat(o.decision()).isEqualTo(RoutingDecision.AUTO_REJECT);
        assertThat(o.spread()).isEqualTo(10);
    }

    @Test
    void criticalRedFlagEscalatesEvenAHighScore() {
        var o = ConsensusPolicy.decide(scores(98, 98, 98, 70), List.of("CRITICAL: employment dates are falsified"));
        assertThat(o.decision()).isEqualTo(RoutingDecision.HUMAN_ESCALATION);
        assertThat(o.reason()).contains("critical red flag");
    }

    @Test
    void spreadAboveTwentyFiveEscalates() {
        assertThat(ConsensusPolicy.decide(scores(98, 72, 72, 100), List.of()).decision()).isEqualTo(RoutingDecision.HUMAN_ESCALATION);
        var atLimit = ConsensusPolicy.decide(scores(97, 72, 90, 100), List.of());
        assertThat(atLimit.spread()).isEqualTo(25);
        assertThat(atLimit.decision()).as("a spread of exactly 25 is allowed; composite 86.3").isEqualTo(RoutingDecision.AUTO_APPROVE);
    }

    @Test
    void ambiguousMiddleEscalates() {
        assertThat(ConsensusPolicy.decide(scores(70, 65, 60, 100), List.of()).decision()).isEqualTo(RoutingDecision.HUMAN_ESCALATION);
    }

    @Test
    void scoreReducerReplacesByDimension() {
        var merged = ReviewWorkflowState.mergeByDimension(
                List.of(new DimensionScore("a", 10, "old"), new DimensionScore("b", 20, "")),
                List.of(new DimensionScore("a", 90, "new")));
        assertThat(merged).extracting(DimensionScore::dimension).containsExactly("a", "b");
        assertThat(merged.getFirst().score()).isEqualTo(90);
    }
}
