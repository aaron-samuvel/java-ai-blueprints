package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.List;

/** Log source (Loki in production). Deterministic, no LLM involved. */
public interface LokiLogClient {

    /** Recent error lines for the service (a few, for the first diagnosis). */
    List<String> recentErrors(String serviceName, int limit);

    /** Full trace-correlated logs around the anomaly, for the deep analysis path. */
    List<String> traceLogs(String serviceName, int limit);
}
