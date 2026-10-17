package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CustomerOrder;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.OrderItem;

/**
 * The evaluator half of the evaluator-optimizer loop: hard business rules a refund proposal must
 * pass. It returns the violations as feedback text for the next round (empty = acceptable).
 */
public class RefundPolicy {

    private final int windowDays;

    public RefundPolicy(int windowDays) {
        this.windowDays = windowDays;
    }

    public int windowDays() {
        return windowDays;
    }

    public List<String> evaluate(RefundAndDisputeAgent.RefundProposal p, CustomerOrder order, OrderItem item, LocalDateTime now) {
        List<String> violations = new ArrayList<>();
        if (p == null || p.decision() == null) {
            violations.add("the proposal must state a decision APPROVE or DENY");
            return violations;
        }
        boolean inWindow = Duration.between(order.createdAt(), now).toDays() <= windowDays;
        int refundable = item.quantity() - item.refundedQuantity();
        if (p.decision() == RefundAndDisputeAgent.Decision.APPROVE) {
            if (!inWindow) violations.add("the order is older than the " + windowDays + "-day refund window, so it must be DENY");
            if (p.quantity() < 1 || p.quantity() > refundable) {
                violations.add("quantity must be between 1 and " + refundable + " (units bought minus units already refunded)");
            }
            BigDecimal cap = item.unitPriceInr().multiply(BigDecimal.valueOf(Math.max(1, Math.min(p.quantity(), refundable))));
            if (p.amountInr() == null || p.amountInr().signum() <= 0 || p.amountInr().compareTo(cap) > 0) {
                violations.add("amount must be positive and at most INR " + cap.toPlainString() + " (unit price x quantity, no taxes or fees on top)");
            }
        } else if (inWindow && refundable > 0 && (p.rationale() == null || p.rationale().isBlank())) {
            violations.add("a DENY inside the refund window needs a rationale");
        }
        return violations;
    }
}
