package io.github.aaronsamuvel.blueprints.swarm.commerce;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.AgentReply;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.OrderExecutionAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.ProductDiscoveryAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.RefundAndDisputeAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.Route;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.TriageRouterAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.Turn;
import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantPartitionedChatMemory;
import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantSessionKey;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * The swarm's front door. For each message: load the tenant-partitioned session history, let the
 * triage agent pick a specialist, hand off (recording a handoff badge), then store the exchange
 * back in memory. The tenant is bound for the whole turn, so no agent or tool can see another tenant.
 */
public class AgentOrchestratorService {

    public record Handoff(String from, String to, String reason) {}

    public record SwarmReply(String tenantId, String userId, String sessionId, Route route, List<Handoff> handoffs,
                             String agent, String answer, Map<String, Object> data) {}

    public record HistoryEntry(String role, String content) {}

    private final TriageRouterAgent triage;
    private final ProductDiscoveryAgent discovery;
    private final OrderExecutionAgent orders;
    private final RefundAndDisputeAgent refunds;
    private final ChatClient general;
    private final TenantPartitionedChatMemory memory;

    public AgentOrchestratorService(TriageRouterAgent triage, ProductDiscoveryAgent discovery, OrderExecutionAgent orders,
                                    RefundAndDisputeAgent refunds, ChatClient general, TenantPartitionedChatMemory memory) {
        this.triage = triage;
        this.discovery = discovery;
        this.orders = orders;
        this.refunds = refunds;
        this.general = general;
        this.memory = memory;
    }

    public SwarmReply chat(TenantSessionKey key, String message) {
        return chat(key, message, event -> { });
    }

    /** Same as {@link #chat(TenantSessionKey, String)}, publishing each handoff as it happens (for SSE). */
    public SwarmReply chat(TenantSessionKey key, String message, Consumer<Handoff> onHandoff) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message is required");
        return TenantContext.callAs(key.tenantId(), () -> {
            List<Message> history = memory.get(key.conversationId());
            Turn turn = new Turn(key, message, history);
            List<Handoff> handoffs = new ArrayList<>();
            TriageRouterAgent.TriageDecision decision = triage.route(turn);
            String target = switch (decision.route()) {
                case PRODUCT_DISCOVERY -> ProductDiscoveryAgent.NAME;
                case ORDER_EXECUTION -> OrderExecutionAgent.NAME;
                case REFUND_DISPUTE -> RefundAndDisputeAgent.NAME;
                case GENERAL -> "general";
            };
            Handoff handoff = new Handoff(TriageRouterAgent.NAME, target, decision.reason());
            handoffs.add(handoff);
            onHandoff.accept(handoff);
            AgentReply reply = switch (decision.route()) {
                case PRODUCT_DISCOVERY -> discovery.handle(turn);
                case ORDER_EXECUTION -> orders.handle(turn);
                case REFUND_DISPUTE -> refunds.handle(turn);
                case GENERAL -> new AgentReply("general", general.prompt()
                        .system("TASK: general\nYou are a shop assistant. Offer help with products, orders and refunds.")
                        .messages(history).user(message).call().content(), Map.of());
            };
            memory.add(key.conversationId(), List.of(new UserMessage(message), new AssistantMessage(reply.answer())));
            return new SwarmReply(key.tenantId(), key.userId(), key.sessionId(), decision.route(), handoffs, reply.agent(), reply.answer(), reply.data());
        });
    }

    public List<HistoryEntry> history(TenantSessionKey key) {
        return TenantContext.callAs(key.tenantId(), () -> memory.get(key.conversationId()).stream()
                .map(m -> new HistoryEntry(m.getMessageType().name(), m.getText())).toList());
    }
}
