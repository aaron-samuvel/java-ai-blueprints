package io.github.aaronsamuvel.blueprints.eval;

import java.util.List;

/** Aggregate result of an evaluation run. A case passes only if both evaluators pass it. */
public record GateReport(int total, int relevancyPassed, int factPassed, List<String> failures) {

    public GateReport {
        failures = List.copyOf(failures);
    }

    public double relevancyRate() {
        return total == 0 ? 1.0 : (double) relevancyPassed / total;
    }

    public double factRate() {
        return total == 0 ? 1.0 : (double) factPassed / total;
    }

    public boolean passes(double minRelevancy, double minFact) {
        return relevancyRate() >= minRelevancy && factRate() >= minFact;
    }

    public String summary() {
        return "cases=%d relevancy=%.0f%% facts=%.0f%% failures=%s"
                .formatted(total, relevancyRate() * 100, factRate() * 100, failures);
    }
}
