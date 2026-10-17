package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.Map;

/** Metrics source (Prometheus in production). Deterministic, no LLM involved. */
public interface PrometheusMetricsClient {

    /** Utilisation ratios such as cpu, memory, for the service's pods. */
    Map<String, Double> resourceUtilization(String serviceName);

    /** SHA of the most recent deployment (from the kube_deployment labels). */
    String latestDeploymentSha(String serviceName);
}
