package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.util.Map;

/** What a specialist agent returns: its name, the customer-facing answer, and structured data. */
public record AgentReply(String agent, String answer, Map<String, Object> data) {
    public AgentReply {
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
