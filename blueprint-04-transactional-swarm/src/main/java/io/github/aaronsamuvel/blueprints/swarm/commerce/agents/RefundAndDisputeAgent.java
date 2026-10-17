package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.OrderRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.RefundCaseRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CustomerOrder;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.OrderItem;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.RefundCase;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refunds and disputes. It needs the customer's {@code userId} (from the session key) and an
 * {@code orderItemId} (from the message); it refuses to act on an item the customer doesn't own.
 * The decision is made by an <b>evaluator-optimizer loop</b>: the model proposes, {@link RefundPolicy}
 * evaluates, and violations are fed back for up to {@code maxRounds} rounds. A proposal that never
 * passes is escalated to a person instead of being paid. The accepted outcome is written in one transaction.
 */
public class RefundAndDisputeAgent {

    public static final String NAME = "refund-dispute";

    public enum Decision { APPROVE, DENY }

    public record RefundRequestDetails(@JsonPropertyDescription("The order item id the customer refers to, null if not given") Long orderItemId,
                                       @JsonPropertyDescription("Units to refund, 1 if not stated") Integer quantity,
                                       @JsonPropertyDescription("The customer's reason") String reason) {}

    public record RefundProposal(@JsonPropertyDescription("APPROVE or DENY") Decision decision,
                                 @JsonPropertyDescription("Units to refund") int quantity,
                                 @JsonPropertyDescription("Amount to refund in INR") BigDecimal amountInr,
                                 @JsonPropertyDescription("Short justification for the customer") String rationale) {}

    private final ChatClient chat;
    private final OrderRepository orders;
    private final RefundCaseRepository refunds;
    private final RefundPolicy policy;
    private final TransactionTemplate tx;
    private final int maxRounds;
    private final Clock clock;

    public RefundAndDisputeAgent(ChatClient chat, OrderRepository orders, RefundCaseRepository refunds, RefundPolicy policy,
                                 TransactionTemplate tx, int maxRounds, Clock clock) {
        this.chat = chat;
        this.orders = orders;
        this.refunds = refunds;
        this.policy = policy;
        this.tx = tx;
        this.maxRounds = maxRounds;
        this.clock = clock;
    }

    public AgentReply handle(Turn turn) {
        String tenant = turn.key().tenantId();
        String user = turn.key().userId();
        RefundRequestDetails details = chat.prompt()
                .system("TASK: refund-extract\nExtract the order item id, quantity and reason from the customer's refund or dispute request.")
                .messages(turn.history()).user(turn.message()).call().entity(RefundRequestDetails.class);
        if (details == null || details.orderItemId() == null) {
            return new AgentReply(NAME, "I can help with that refund. Which order item is it? Please give the order item id from your receipt.",
                    Map.of("needs", "orderItemId"));
        }
        Optional<CustomerOrder> owned = orders.findByItemId(tenant, details.orderItemId()).filter(o -> o.userId().equals(user));
        if (owned.isEmpty()) {
            return new AgentReply(NAME, "I couldn't find order item " + details.orderItemId() + " on your account.",
                    Map.of("orderItemId", details.orderItemId(), "found", false));
        }
        CustomerOrder order = owned.get();
        OrderItem item = order.items().stream().filter(i -> i.id().equals(details.orderItemId())).findFirst().orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock);
        String facts = "Order " + order.id() + " placed " + order.createdAt().toLocalDate() + "; item " + item.id() + ": " + item.quantity()
                + " x " + item.sku() + " at INR " + item.unitPriceInr() + " each, " + item.refundedQuantity() + " already refunded. Today is "
                + now.toLocalDate() + ". Refund window: " + policy.windowDays() + " days. Requested quantity: "
                + (details.quantity() == null ? 1 : details.quantity()) + ". Reason: " + details.reason();

        List<Map<String, Object>> rounds = new ArrayList<>();
        RefundProposal accepted = null;
        List<String> feedback = List.of();
        for (int round = 1; round <= maxRounds && accepted == null; round++) {
            String system = "TASK: refund-propose\nYou are the refund agent. Propose a decision.\nFacts: " + facts
                    + (feedback.isEmpty() ? "" : "\nPolicy feedback on your previous proposal: " + String.join("; ", feedback));
            RefundProposal proposal = chat.prompt().system(system).user(turn.message()).call().entity(RefundProposal.class);
            feedback = policy.evaluate(proposal, order, item, now);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("round", round);
            r.put("proposal", proposal);
            r.put("violations", feedback);
            rounds.add(r);
            if (feedback.isEmpty()) accepted = proposal;
        }
        if (accepted == null) {
            return new AgentReply(NAME, "I've passed your request for order item " + item.id() + " to a support specialist, who will reply within one working day.",
                    Map.of("decision", "ESCALATED", "rounds", rounds));
        }
        RefundProposal decision = accepted;
        RefundCase saved = tx.execute(status -> {
            if (decision.decision() == Decision.APPROVE) {
                orders.save(order.withItems(order.items().stream()
                        .map(i -> i.id().equals(item.id()) ? i.withRefunded(decision.quantity()) : i).toList()));
            }
            return refunds.save(new RefundCase(null, tenant, user, order.id(), item.id(),
                    decision.decision() == Decision.APPROVE ? decision.quantity() : 0,
                    decision.decision() == Decision.APPROVE ? decision.amountInr() : BigDecimal.ZERO,
                    decision.decision().name(), decision.rationale(), rounds.size(), now));
        });
        String answer = decision.decision() == Decision.APPROVE
                ? "Your refund of INR " + decision.amountInr().toPlainString() + " for " + decision.quantity() + " x " + item.sku()
                  + " is approved (case " + saved.id() + "). " + decision.rationale()
                : "I'm sorry, the refund for order item " + item.id() + " can't be approved: " + decision.rationale();
        return new AgentReply(NAME, answer, Map.of("decision", decision.decision().name(), "refundCaseId", saved.id(), "rounds", rounds));
    }
}
