package io.github.aaronsamuvel.blueprints.goap.ooda;

import java.math.BigDecimal;
import java.util.List;

/** What the agent did and why. {@code trace} lists every OODA cycle: plan chosen, action executed. */
public record IncidentReport(Outcome outcome, String service, BigDecimal todayInr, BigDecimal baselineInr,
                             CostDiagnosis.Cause cause, List<String> trace) {
    public enum Outcome { NO_INCIDENT, RESOLVED, ESCALATED }

    public IncidentReport {
        trace = List.copyOf(trace);
    }
}
