package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** The {@code runbook-search} tool group: the only tools the diagnosis step may call. */
public class RunbookSearchTools {

    private static final Map<String, String> RUNBOOKS = Map.of(
            "connection pool", "RB-12: Pool exhaustion after a deploy usually means a config regression; roll back and restore maximumPoolSize.",
            "retry storm", "RB-07: Retry storms amplify DB latency; enable jittered backoff and a circuit breaker.",
            "idle replicas", "RB-21: Idle replicas for hours: lower minReplicas and right-size the node pool.");

    @Tool(description = "Search the incident runbooks for a symptom and return matching runbook entries")
    public List<String> searchRunbooks(@ToolParam(description = "Symptom keywords, e.g. 'connection pool'") String symptom) {
        String q = symptom == null ? "" : symptom.toLowerCase(Locale.ROOT);
        return RUNBOOKS.entrySet().stream().filter(e -> q.contains(e.getKey()) || e.getKey().contains(q))
                .map(Map.Entry::getValue).sorted().toList();
    }
}
