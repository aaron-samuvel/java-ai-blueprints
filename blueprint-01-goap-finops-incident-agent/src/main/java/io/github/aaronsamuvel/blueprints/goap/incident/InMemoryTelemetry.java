package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.List;
import java.util.Map;

/**
 * Offline stand-in for Prometheus and Loki with synthetic data for two services. Swap in HTTP
 * clients that query {@code /api/v1/query} and {@code /loki/api/v1/query_range} for production.
 */
public class InMemoryTelemetry implements PrometheusMetricsClient, LokiLogClient {

    private static final Map<String, Map<String, Double>> UTIL = Map.of(
            "payment-service", Map.of("cpu", 0.97, "memory", 0.71),
            "search-api", Map.of("cpu", 0.08, "memory", 0.22));
    private static final Map<String, String> SHA = Map.of("payment-service", "a1b2c3d", "search-api", "9f8e7d6");
    private static final Map<String, List<String>> ERRORS = Map.of(
            "payment-service", List.of(
                    "ERROR c.p.PaymentController - timeout calling ledger-db after 3000 ms",
                    "WARN  HikariPool-1 - Connection is not available, request timed out after 30000ms",
                    "ERROR c.p.RetryInterceptor - retry storm: 4512 retries in 60s"),
            "search-api", List.of("INFO  autoscaler - 12 replicas idle for 6h"));
    private static final Map<String, List<String>> TRACES = Map.of(
            "payment-service", List.of(
                    "trace=7f1 span=ledger-db.query db.statement='SELECT ... FOR UPDATE' duration=2950ms",
                    "trace=7f1 deploy a1b2c3d changed hikari.maximumPoolSize 50 -> 5",
                    "trace=7f2 span=ledger-db.query waiting for connection 29800ms"),
            "search-api", List.of("trace=c01 autoscaler minReplicas=12 while qps=3"));

    @Override
    public Map<String, Double> resourceUtilization(String s) { return UTIL.getOrDefault(s, Map.of()); }

    @Override
    public String latestDeploymentSha(String s) { return SHA.getOrDefault(s, "unknown"); }

    @Override
    public List<String> recentErrors(String s, int limit) { return ERRORS.getOrDefault(s, List.of()).stream().limit(limit).toList(); }

    @Override
    public List<String> traceLogs(String s, int limit) { return TRACES.getOrDefault(s, List.of()).stream().limit(limit).toList(); }
}
