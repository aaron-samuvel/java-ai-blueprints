package io.github.aaronsamuvel.blueprints.goap.incident;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * Offline, deterministic model for the incident agent (default when {@code blueprints.model=stub}).
 * It reads the {@code TASK:} marker of each prompt and answers with JSON for the expected record.
 * Its first diagnosis of payment-service is deliberately low-confidence (0.55) so the demo shows
 * the deep-trace replanning path; search-api is diagnosed with high confidence straight away.
 */
public final class DemoIncidentModel {

    private static final Pattern SERVICE = Pattern.compile("\\b([a-z][a-z0-9]*-[a-z0-9-]*[a-z0-9])\\b");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");

    private DemoIncidentModel() {}

    public static StubChatModel create() {
        return new StubChatModel(DemoIncidentModel::answer);
    }

    static String answer(Prompt p) {
        String t = StubChatModel.lastText(p);
        if (t.contains("TASK: parse-alert")) return parse(t.substring(t.indexOf("ALERT:") + 6));
        boolean payment = t.contains("payment-service");
        if (t.contains("TASK: diagnose")) {
            return payment
                    ? hypothesis("DEPENDENCY_FAILURE", 0.55, "ledger-db timeouts and a retry storm; cause of the slowdown unclear", false, false)
                    : hypothesis("OVER_PROVISIONED", 0.91, "12 idle replicas at 8% CPU (runbook RB-21)", false, true);
        }
        if (t.contains("TASK: deep-trace")) {
            return hypothesis("BAD_DEPLOYMENT", 0.92,
                    "deploy a1b2c3d cut hikari.maximumPoolSize from 50 to 5; connections wait ~30 s (runbook RB-12)", true, false);
        }
        if (t.contains("TASK: synthesize")) {
            return payment ? """
                    {"incidentId":"x","executiveSummary":"Deploy a1b2c3d shrank the DB connection pool, causing payment timeouts. Roll back now and restore the pool size.",
                     "kubectlOrTerraformCommands":["kubectl rollout undo deployment/payment-service","kubectl rollout status deployment/payment-service"],
                     "pullRequestDiff":"--- a/payment-service/application.yml\\n+++ b/payment-service/application.yml\\n-    maximum-pool-size: 5\\n+    maximum-pool-size: 50",
                     "estimatedMonthlySavingsUsd":0}""" : """
                    {"incidentId":"x","executiveSummary":"search-api runs 12 idle replicas. Lower minReplicas to 3 to cut spend.",
                     "kubectlOrTerraformCommands":["kubectl patch hpa search-api -p '{\\"spec\\":{\\"minReplicas\\":3}}'"],
                     "pullRequestDiff":"--- a/search-api/hpa.yaml\\n+++ b/search-api/hpa.yaml\\n-  minReplicas: 12\\n+  minReplicas: 3",
                     "estimatedMonthlySavingsUsd":1840}""";
        }
        return "{}";
    }

    private static String parse(String alert) {
        Matcher s = SERVICE.matcher(alert);
        String service = s.find() ? s.group(1) : "unknown-service";
        Matcher n = NUMBER.matcher(alert);
        double observed = n.find() ? Double.parseDouble(n.group(1)) : 0;
        double threshold = n.find() ? Double.parseDouble(n.group(1)) : 0;
        String metric = alert.toLowerCase().contains("cost") || alert.contains("$") ? "daily_cost_usd" : "p99_latency_ms";
        return """
                {"serviceName":"%s","metricType":"%s","observedValue":%s,"threshold":%s,"timestamp":"%s"}"""
                .formatted(service, metric, observed, threshold, Instant.parse("2026-10-17T09:30:00Z"));
    }

    private static String hypothesis(String cat, double conf, String evidence, boolean rollback, boolean rightSize) {
        return """
                {"category":"%s","confidenceScore":%s,"evidenceSummary":"%s","requiresRollback":%s,"requiresRightSizing":%s}"""
                .formatted(cat, conf, evidence, rollback, rightSize);
    }
}
