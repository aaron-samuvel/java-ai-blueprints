package io.github.aaronsamuvel.blueprints.swarm.agents;

import java.math.BigDecimal;
import java.util.Locale;

import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Three cooperating agents sharing one tenant-partitioned conversation memory:
 * triage (route), refund (decide and write the refund row), notifier (write an outbox message).
 * None of them opens a transaction; the coordinator owns it.
 */
public class SwarmAgents {

    public static final String TRIAGE_PROMPT = "You are the TRIAGE agent. Reply with exactly one word: REFUND or OTHER.";
    public static final String REFUND_PROMPT = "You are the REFUND agent. Decide the refund for the customer's order.";
    public static final String NOTIFY_PROMPT = "You are the NOTIFY agent. Write a one-line customer notification.";

    private final ChatClient client;
    private final JdbcTemplate jdbc;
    private final BigDecimal maxRefundInr;

    public SwarmAgents(ChatModel model, ChatMemory memory, JdbcTemplate jdbc, BigDecimal maxRefundInr) {
        this.client = ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
        this.jdbc = jdbc;
        this.maxRefundInr = maxRefundInr;
    }

    public String triage(String conversationId, String request) {
        String out = client.prompt().system(TRIAGE_PROMPT).user(request)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();
        return out == null ? "OTHER" : out.strip().toUpperCase(Locale.ROOT);
    }

    public RefundDecision refund(String conversationId, String request) {
        RefundDecision d = client.prompt().system(REFUND_PROMPT).user(request)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().entity(RefundDecision.class);
        if (d == null || d.amountInr() == null || d.amountInr().signum() <= 0) {
            throw new IllegalStateException("refund agent returned no valid decision");
        }
        if (d.amountInr().compareTo(maxRefundInr) > 0) {
            throw new RefundLimitExceededException("refund " + d.amountInr() + " exceeds limit " + maxRefundInr);
        }
        jdbc.update("INSERT INTO refunds (tenant_id, order_id, amount_inr, reason) VALUES (?,?,?,?)",
                TenantContext.require(), d.orderId(), d.amountInr(), d.reason());
        return d;
    }

    public String notifyCustomer(String conversationId, RefundDecision d) {
        String msg = client.prompt().system(NOTIFY_PROMPT)
                .user("Refund of INR " + d.amountInr() + " approved for order " + d.orderId())
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call().content();
        if (msg == null || msg.isBlank()) throw new IllegalStateException("notification agent produced an empty message");
        jdbc.update("INSERT INTO outbox (tenant_id, payload) VALUES (?,?)", TenantContext.require(), msg.strip());
        return msg.strip();
    }
}
