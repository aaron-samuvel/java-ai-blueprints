package io.github.aaronsamuvel.blueprints.nl2sql.a2a;

import java.util.List;

/** Discovery document, A2A-style (served at /.well-known/agent.json). Simplified subset. */
public record AgentCard(String name, String description, String url, String version, List<Skill> skills) {
    public record Skill(String id, String name, String description, List<String> examples) {}
}
