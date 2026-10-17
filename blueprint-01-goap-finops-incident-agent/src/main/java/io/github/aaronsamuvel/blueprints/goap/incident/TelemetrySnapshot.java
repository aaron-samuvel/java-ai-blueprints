package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Metrics and logs collected deterministically for one service")
public record TelemetrySnapshot(
        @JsonPropertyDescription("Service the telemetry belongs to") String serviceName,
        @JsonPropertyDescription("Most recent error log lines") List<String> recentErrorLogs,
        @JsonPropertyDescription("Resource utilisation ratios, e.g. cpu=0.93") Map<String, Double> resourceUtilization,
        @JsonPropertyDescription("Git SHA of the latest deployment") String recentDeploymentSha) {
    public TelemetrySnapshot {
        recentErrorLogs = List.copyOf(recentErrorLogs);
        resourceUtilization = Map.copyOf(resourceUtilization);
    }
}
