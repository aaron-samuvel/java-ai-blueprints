package io.github.aaronsamuvel.blueprints.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.goap.incident.CloudRemediationAgent;
import io.github.aaronsamuvel.blueprints.goap.incident.DemoIncidentModel;
import io.github.aaronsamuvel.blueprints.goap.incident.InMemoryTelemetry;
import io.github.aaronsamuvel.blueprints.goap.incident.RemediationPlan;
import io.github.aaronsamuvel.blueprints.goap.incident.RunbookSearchTools;
import io.github.aaronsamuvel.blueprints.goap.platform.AgentPlatform;
import io.github.aaronsamuvel.blueprints.goap.platform.AgentProcessResult;
import io.github.aaronsamuvel.blueprints.goap.platform.LlmInvocation;
import io.github.aaronsamuvel.blueprints.goap.platform.LlmOptions;
import io.github.aaronsamuvel.blueprints.goap.platform.ToolGroups;
import io.github.aaronsamuvel.blueprints.goap.platform.UserInput;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Deterministic tests of the handbook's Blueprint 1 acceptance criteria. Every LLM call is recorded
 * ({@link LlmInvocation}) and served by a stub model, the role Embabel's FakeOperationContext plays.
 */
class CloudRemediationAgentTest {

    static final String PAYMENT_ALERT = "Investigate latency spike on payment-service: p99 2950 ms over threshold 500 ms";
    static final String SEARCH_ALERT = "Cost anomaly on search-api: daily cost 310 USD over threshold 120 USD";

    final InMemoryTelemetry telemetry = new InMemoryTelemetry();
    final CloudRemediationAgent agent = new CloudRemediationAgent(telemetry, telemetry, LlmOptions.fast(), LlmOptions.frontier());

    AgentPlatform platform(StubChatModel model) {
        return new AgentPlatform(Map.of(LlmOptions.FAST, model, LlmOptions.FRONTIER, model),
                ToolGroups.of(CloudRemediationAgent.RUNBOOK_SEARCH, new RunbookSearchTools()), 12);
    }

    static LlmInvocation call(AgentProcessResult<?> r, String action) {
        return r.llmInvocations().stream().filter(i -> i.action().equals(action)).findFirst().orElseThrow();
    }

    @Test
    void lowConfidenceTriggersDeepTraceReplanningBeforeSynthesis() {
        StubChatModel model = DemoIncidentModel.create();
        AgentProcessResult<RemediationPlan> r = platform(model).run(agent, new UserInput(PAYMENT_ALERT));

        assertThat(r.goalAchieved()).isTrue();
        assertThat(r.actionsRun()).containsExactly(
                "parseAlert", "collectTelemetry", "diagnoseRootCause", "deepLogTraceAnalysis", "synthesizeRemediation");
        // replanning is visible in the trace: after diagnosis the planner sees !isHighConfidence
        assertThat(r.trace().get(3)).contains("!isHighConfidence").contains("act=deepLogTraceAnalysis");
        assertThat(r.output().incidentId()).startsWith("INC-payment-service-");
        assertThat(r.output().kubectlOrTerraformCommands()).contains("kubectl rollout undo deployment/payment-service");
    }

    @Test
    void highConfidenceGoesStraightToSynthesis() {
        AgentProcessResult<RemediationPlan> r = platform(DemoIncidentModel.create()).run(agent, new UserInput(SEARCH_ALERT));
        assertThat(r.goalAchieved()).isTrue();
        assertThat(r.actionsRun()).containsExactly("parseAlert", "collectTelemetry", "diagnoseRootCause", "synthesizeRemediation");
        assertThat(r.output().estimatedMonthlySavingsUsd()).isEqualTo(1840);
    }

    @Test
    void diagnosisPromptContainsExactServiceNameAndLogSnippets() {
        AgentProcessResult<RemediationPlan> r = platform(DemoIncidentModel.create()).run(agent, new UserInput(PAYMENT_ALERT));
        String prompt = call(r, "diagnoseRootCause").prompt();
        assertThat(prompt).contains("Service: payment-service")
                .contains("ERROR c.p.PaymentController - timeout calling ledger-db after 3000 ms")
                .contains("retry storm: 4512 retries in 60s")
                .contains("Latest deployment: a1b2c3d");
        assertThat(call(r, "deepLogTraceAnalysis").prompt()).contains("hikari.maximumPoolSize 50 -> 5");
    }

    @Test
    void collectTelemetryMakesZeroLlmCalls() {
        StubChatModel model = DemoIncidentModel.create();
        AgentProcessResult<RemediationPlan> r = platform(model).run(agent, new UserInput(PAYMENT_ALERT));
        assertThat(r.actionsRun()).contains("collectTelemetry");
        assertThat(r.llmInvocations()).extracting(LlmInvocation::action).doesNotContain("collectTelemetry")
                .containsExactly("parseAlert", "diagnoseRootCause", "deepLogTraceAnalysis", "synthesizeRemediation");
        // the model saw exactly the recorded calls, nothing more
        assertThat(model.received()).hasSize(4);
    }

    @Test
    void parseAlertRunsAtTemperatureZeroOnTheFastModelWithNoTools() {
        StubChatModel model = DemoIncidentModel.create();
        AgentProcessResult<RemediationPlan> r = platform(model).run(agent, new UserInput(PAYMENT_ALERT));
        LlmInvocation parse = call(r, "parseAlert");
        assertThat(parse.options().role()).isEqualTo(LlmOptions.FAST);
        assertThat(parse.options().temperature()).isEqualTo(0.0);
        assertThat(parse.toolNames()).isEmpty();
        Prompt sent = model.received().get(0);
        assertThat(sent.getOptions().getTemperature()).isEqualTo(0.0);
        assertThat(call(r, "synthesizeRemediation").options().role()).isEqualTo(LlmOptions.FRONTIER);
    }

    @Test
    void diagnosisSeesOnlyTheRunbookSearchToolGroup() {
        AgentProcessResult<RemediationPlan> r = platform(DemoIncidentModel.create()).run(agent, new UserInput(PAYMENT_ALERT));
        assertThat(call(r, "diagnoseRootCause").toolNames()).containsExactly("searchRunbooks");
        assertThat(call(r, "synthesizeRemediation").toolNames()).isEmpty();
    }

    @Test
    void confidenceThatStaysLowEndsWithoutAPlanAfterOneDeepTrace() {
        StubChatModel alwaysUnsure = new StubChatModel(p -> {
            String t = StubChatModel.lastText(p);
            if (t.contains("TASK: parse-alert")) return DemoIncidentModel.create().call(p).getResult().getOutput().getText();
            return """
                    {"category":"UNKNOWN","confidenceScore":0.4,"evidenceSummary":"inconclusive","requiresRollback":false,"requiresRightSizing":false}""";
        });
        AgentProcessResult<RemediationPlan> r = platform(alwaysUnsure).run(agent, new UserInput(PAYMENT_ALERT));
        assertThat(r.goalAchieved()).isFalse();
        assertThat(r.output()).isNull();
        assertThat(r.actionsRun()).containsExactly("parseAlert", "collectTelemetry", "diagnoseRootCause", "deepLogTraceAnalysis");
        assertThat(r.trace().get(r.trace().size() - 1)).contains("no plan reaches RemediationPlan");
    }

    @Test
    void remediationPlanRendersMarkdown() {
        RemediationPlan p = new RemediationPlan("INC-1", "Roll back.", List.of("kubectl rollout undo deployment/x"), "-a\n+b", 0);
        assertThat(p.content()).contains("## Remediation plan INC-1").contains("`kubectl rollout undo deployment/x`").contains("```diff");
    }
}
