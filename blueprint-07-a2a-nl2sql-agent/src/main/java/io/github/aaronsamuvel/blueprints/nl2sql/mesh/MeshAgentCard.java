package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.util.List;
import java.util.Map;

/**
 * Agent card published to the registry: supported intents, input/output JSON schemas and live
 * SLA metrics (handbook Part III, Blueprint 7). {@code url} is the agent's A2A JSON-RPC endpoint.
 */
public record MeshAgentCard(String name, String description, String url, String version, List<String> intents,
                            Map<String, Object> inputSchema, Map<String, Object> outputSchema, SlaMetrics sla) {

    public record SlaMetrics(long calls, double successRate, long p95LatencyMs) {
        public static final SlaMetrics NONE = new SlaMetrics(0, 1.0, 0);
    }
}
