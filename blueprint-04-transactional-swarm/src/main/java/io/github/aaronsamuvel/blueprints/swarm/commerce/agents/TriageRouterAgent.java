package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import org.springframework.ai.chat.client.ChatClient;

/** Classifies each message into a {@link Route}. It never answers the customer itself. */
public class TriageRouterAgent {

    public static final String NAME = "triage-router";
    public static final String SYSTEM = """
            TASK: triage
            You are the triage router of a shop's support swarm. Pick exactly one route:
            PRODUCT_DISCOVERY (finding or comparing products), ORDER_EXECUTION (placing an order or checking its status),
            REFUND_DISPUTE (refunds, returns, damaged items, disputes), GENERAL (anything else).""";

    public record TriageDecision(@JsonPropertyDescription("One of PRODUCT_DISCOVERY, ORDER_EXECUTION, REFUND_DISPUTE, GENERAL") Route route,
                                 @JsonPropertyDescription("Why this route, in a few words") String reason) {}

    private final ChatClient chat;

    public TriageRouterAgent(ChatClient chat) {
        this.chat = chat;
    }

    public TriageDecision route(Turn turn) {
        TriageDecision d = chat.prompt().system(SYSTEM).messages(turn.history()).user(turn.message())
                .call().entity(TriageDecision.class);
        return d == null || d.route() == null ? new TriageDecision(Route.GENERAL, "no decision") : d;
    }
}
