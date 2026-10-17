package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.util.List;

/**
 * Where agents publish their cards and find each other. The handbook names Nacos or Spring Cloud
 * Discovery; this repository ships {@link LocalAgentRegistry} (in-process, TTL-based) behind this
 * interface, and a Nacos-backed implementation would plug in here.
 */
public interface AgentRegistry {

    void register(MeshAgentCard card);

    /** Live cards that declare {@code intent}, best SLA first. */
    List<MeshAgentCard> discover(String intent);

    List<MeshAgentCard> all();
}
