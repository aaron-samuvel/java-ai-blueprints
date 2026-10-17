package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.goap.platform.AchievesGoal;
import io.github.aaronsamuvel.blueprints.goap.platform.Action;
import io.github.aaronsamuvel.blueprints.goap.platform.Agent;
import io.github.aaronsamuvel.blueprints.goap.platform.Ai;
import io.github.aaronsamuvel.blueprints.goap.platform.Condition;
import io.github.aaronsamuvel.blueprints.goap.platform.LlmOptions;
import io.github.aaronsamuvel.blueprints.goap.platform.UserInput;

/**
 * The handbook's Blueprint 1 agent. The planner derives the order of these actions from their
 * parameter and return types: {@code UserInput -> AnomalyAlert -> TelemetrySnapshot -> RootCauseHypothesis
 * -> RemediationPlan}. When {@link #isHighConfidence} turns out false, the plan changes to
 * {@code deepLogTraceAnalysis} before synthesis (the OODA replanning path).
 */
@Agent(description = "Diagnose cloud anomalies and produce a verified remediation plan")
public class CloudRemediationAgent {

    public static final double HIGH_CONFIDENCE = 0.80;
    public static final String RUNBOOK_SEARCH = "runbook-search";

    private final PrometheusMetricsClient metrics;
    private final LokiLogClient logs;
    private final LlmOptions fast;
    private final LlmOptions frontier;

    public CloudRemediationAgent(PrometheusMetricsClient metrics, LokiLogClient logs, LlmOptions fast, LlmOptions frontier) {
        this.metrics = metrics;
        this.logs = logs;
        this.fast = fast;
        this.frontier = frontier;
    }

    @Action(description = "Extract a structured alert with a deterministic, low-temperature call")
    public AnomalyAlert parseAlert(UserInput input, Ai ai) {
        return ai.withLlm(fast.withTemperature(0.0)).createObject("""
                TASK: parse-alert
                Extract the anomaly from this alert. Use the exact service name.
                ALERT: %s""".formatted(input.content()), AnomalyAlert.class);
    }

    /** Pure Java: zero LLM calls (no {@link Ai} parameter, so it cannot make one). */
    @Action(description = "Collect metrics and logs from Prometheus and Loki")
    public TelemetrySnapshot collectTelemetry(AnomalyAlert alert) {
        String s = alert.serviceName();
        return new TelemetrySnapshot(s, logs.recentErrors(s, 5), metrics.resourceUtilization(s), metrics.latestDeploymentSha(s));
    }

    @Action(description = "Correlate telemetry with runbooks", toolGroups = RUNBOOK_SEARCH)
    public RootCauseHypothesis diagnoseRootCause(AnomalyAlert alert, TelemetrySnapshot telemetry, Ai ai) {
        return ai.withLlm(frontier).createObject("""
                TASK: diagnose
                Service: %s
                Anomaly: %s observed %s (threshold %s) at %s
                Latest deployment: %s
                Resource utilisation: %s
                Recent error logs:
                %s
                Search the runbooks for the symptoms, then give the most likely root cause with a confidence score.
                """.formatted(alert.serviceName(), alert.metricType(), alert.observedValue(), alert.threshold(), alert.timestamp(),
                telemetry.recentDeploymentSha(), telemetry.resourceUtilization(),
                telemetry.recentErrorLogs().stream().map(l -> "- " + l).collect(Collectors.joining("\n"))),
                RootCauseHypothesis.class);
    }

    @Condition
    public boolean isHighConfidence(RootCauseHypothesis hypothesis) {
        return hypothesis.confidenceScore() >= HIGH_CONFIDENCE;
    }

    @Action(description = "Low confidence: pull trace-correlated logs and re-diagnose",
            pre = "!isHighConfidence", post = "isHighConfidence", canRerun = false, cost = 3, toolGroups = RUNBOOK_SEARCH)
    public RootCauseHypothesis deepLogTraceAnalysis(AnomalyAlert alert, TelemetrySnapshot telemetry,
                                                    RootCauseHypothesis hypothesis, Ai ai) {
        return ai.withLlm(frontier).createObject("""
                TASK: deep-trace
                Service: %s
                The first diagnosis was %s with confidence %s: %s
                Trace-correlated logs:
                %s
                Re-assess the root cause using this evidence.
                """.formatted(alert.serviceName(), hypothesis.category(), hypothesis.confidenceScore(), hypothesis.evidenceSummary(),
                logs.traceLogs(alert.serviceName(), 20).stream().map(l -> "- " + l).collect(Collectors.joining("\n"))),
                RootCauseHypothesis.class);
    }

    @AchievesGoal(description = "Produce an actionable remediation and FinOps plan")
    @Action(description = "Write the remediation plan", pre = "isHighConfidence")
    public RemediationPlan synthesizeRemediation(AnomalyAlert alert, TelemetrySnapshot snapshot, RootCauseHypothesis hypothesis, Ai ai) {
        String incidentId = "INC-" + alert.serviceName() + "-" + alert.timestamp().getEpochSecond();
        RemediationPlan plan = ai.withLlm(frontier).createObject("""
                TASK: synthesize
                Incident: %s
                Service: %s (deployment %s, utilisation %s)
                Root cause: %s (confidence %s): %s
                Rollback required: %s. Right-sizing required: %s.
                Write kubectl or terraform commands, a pull-request diff and the monthly savings estimate.
                """.formatted(incidentId, alert.serviceName(), snapshot.recentDeploymentSha(), snapshot.resourceUtilization(),
                hypothesis.category(), hypothesis.confidenceScore(), hypothesis.evidenceSummary(),
                hypothesis.requiresRollback(), hypothesis.requiresRightSizing()), RemediationPlan.class);
        // the id is ours, not the model's
        return new RemediationPlan(incidentId, plan.executiveSummary(), plan.kubectlOrTerraformCommands(),
                plan.pullRequestDiff(), plan.estimatedMonthlySavingsUsd());
    }
}
