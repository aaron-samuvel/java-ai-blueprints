package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;
import java.util.function.Predicate;

/**
 * The routing rules of the consensus aggregator, kept free of the graph so they can be unit tested.
 * <ol>
 *   <li>any {@code CRITICAL:} red flag, or a spread of more than {@value #MAX_SPREAD} points between the
 *       capability evaluators: {@link RoutingDecision#HUMAN_ESCALATION};</li>
 *   <li>composite of at least {@value #APPROVE_AT} and no red flags at all: {@link RoutingDecision#AUTO_APPROVE};</li>
 *   <li>composite below {@value #REJECT_BELOW}: {@link RoutingDecision#AUTO_REJECT};</li>
 *   <li>everything else (the ambiguous middle, or a high score with minor flags): human escalation.</li>
 * </ol>
 * The composite and the spread are taken over the three capability dimensions. The red-flag auditor's
 * own integrity score is reported but not averaged in: its job is to veto, not to dilute (author's
 * design; the handbook does not say how the audit score is weighed).
 */
public final class ConsensusPolicy {

    public static final int APPROVE_AT = 85;
    public static final int REJECT_BELOW = 50;
    public static final int MAX_SPREAD = 25;

    public record Outcome(double compositeScore, int spread, RoutingDecision decision, String reason) {}

    private ConsensusPolicy() {}

    public static Outcome decide(List<DimensionScore> scores, List<String> redFlags) {
        List<Integer> capability = scores.stream()
                .filter(Predicate.not(s -> RedFlagAuditNode.DIMENSION.equals(s.dimension())))
                .map(DimensionScore::score).toList();
        if (capability.isEmpty()) {
            return new Outcome(0, 0, RoutingDecision.HUMAN_ESCALATION, "no evaluator produced a score");
        }
        double composite = Math.round(capability.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;
        int spread = capability.stream().mapToInt(Integer::intValue).max().orElse(0)
                - capability.stream().mapToInt(Integer::intValue).min().orElse(0);
        List<String> critical = redFlags.stream().filter(f -> f.startsWith("CRITICAL")).toList();
        if (!critical.isEmpty()) {
            return new Outcome(composite, spread, RoutingDecision.HUMAN_ESCALATION, "critical red flag: " + critical.getFirst());
        }
        if (spread > MAX_SPREAD) {
            return new Outcome(composite, spread, RoutingDecision.HUMAN_ESCALATION,
                    "evaluators disagree by " + spread + " points (limit " + MAX_SPREAD + ")");
        }
        if (composite >= APPROVE_AT && redFlags.isEmpty()) {
            return new Outcome(composite, spread, RoutingDecision.AUTO_APPROVE, "composite " + composite + " >= " + APPROVE_AT + " with no red flags");
        }
        if (composite < REJECT_BELOW) {
            return new Outcome(composite, spread, RoutingDecision.AUTO_REJECT, "composite " + composite + " < " + REJECT_BELOW);
        }
        return new Outcome(composite, spread, RoutingDecision.HUMAN_ESCALATION,
                redFlags.isEmpty() ? "composite " + composite + " is in the ambiguous band" : "composite " + composite + " with red flags " + redFlags);
    }
}
