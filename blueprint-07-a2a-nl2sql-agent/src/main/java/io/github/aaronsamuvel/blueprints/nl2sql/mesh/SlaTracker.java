package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Rolling window (last 200 calls) of latency and outcome, published in the agent card. */
public class SlaTracker {

    private final Deque<long[]> window = new ArrayDeque<>();

    public synchronized void record(long latencyMs, boolean success) {
        window.addLast(new long[] {latencyMs, success ? 1 : 0});
        while (window.size() > 200) window.removeFirst();
    }

    public synchronized MeshAgentCard.SlaMetrics snapshot() {
        if (window.isEmpty()) return MeshAgentCard.SlaMetrics.NONE;
        List<Long> latencies = new ArrayList<>();
        long ok = 0;
        for (long[] w : window) {
            latencies.add(w[0]);
            ok += w[1];
        }
        Collections.sort(latencies);
        long p95 = latencies.get(Math.min(latencies.size() - 1, (int) Math.ceil(latencies.size() * 0.95) - 1));
        return new MeshAgentCard.SlaMetrics(window.size(), Math.round(1000.0 * ok / window.size()) / 1000.0, p95);
    }
}
