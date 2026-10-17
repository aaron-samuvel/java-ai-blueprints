package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.util.List;

import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantSessionKey;

import org.springframework.ai.chat.messages.Message;

/** One customer message plus the session history every agent sees (read-only). */
public record Turn(TenantSessionKey key, String message, List<Message> history) {
    public Turn {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
