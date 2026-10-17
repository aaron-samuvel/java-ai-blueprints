package io.github.aaronsamuvel.blueprints.goap.incident;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A production latency or cost anomaly extracted from an alert message")
public record AnomalyAlert(
        @JsonPropertyDescription("Name of the affected service, e.g. payment-service") String serviceName,
        @JsonPropertyDescription("Metric that crossed its threshold, e.g. p99_latency_ms or daily_cost_usd") String metricType,
        @JsonPropertyDescription("Observed metric value") double observedValue,
        @JsonPropertyDescription("Alerting threshold for the metric") double threshold,
        @JsonPropertyDescription("When the anomaly was observed (ISO-8601)") Instant timestamp) {}
