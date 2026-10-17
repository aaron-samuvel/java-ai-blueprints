package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.util.List;
import java.util.Map;

/** Builds this agent's own card (URL known once the web server has a port). */
public class MeshCardFactory {

    private final SlaTracker sla;
    private volatile String baseUrl = "http://localhost:8087";

    public MeshCardFactory(SlaTracker sla) {
        this.sla = sla;
    }

    public void baseUrl(String url) {
        this.baseUrl = url;
    }

    public MeshAgentCard self() {
        return new MeshAgentCard("finance-data-agent", "Self-healing NL2SQL over the tenant's e-commerce data", baseUrl + "/a2a", "0.2.0",
                List.of(A2aJsonRpcController.INTENT),
                Map.of("type", "object", "required", List.of("text", "tenantId"),
                        "properties", Map.of("text", Map.of("type", "string"), "tenantId", Map.of("type", "string"))),
                Map.of("type", "object", "title", "DataAnalysisReport",
                        "properties", Map.of("naturalLanguageSummary", Map.of("type", "string"), "executedSql", Map.of("type", "string"),
                                "rows", Map.of("type", "array"), "suggestedChart", Map.of("type", "object"))),
                sla.snapshot());
    }
}
