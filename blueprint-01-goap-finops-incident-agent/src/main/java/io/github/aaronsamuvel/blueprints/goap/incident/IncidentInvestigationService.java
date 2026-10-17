package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.List;

import io.github.aaronsamuvel.blueprints.goap.platform.AgentPlatform;
import io.github.aaronsamuvel.blueprints.goap.platform.AgentProcessResult;
import io.github.aaronsamuvel.blueprints.goap.platform.LlmInvocation;
import io.github.aaronsamuvel.blueprints.goap.platform.UserInput;

/** Runs {@link CloudRemediationAgent} on the platform and shapes the result for REST and the shell. */
public class IncidentInvestigationService {

    public record LlmCall(String action, String role, Double temperature, List<String> tools) {}

    public record Investigation(boolean goalAchieved, RemediationPlan plan, String markdown, List<String> actionsRun,
                                List<String> trace, List<LlmCall> llmCalls) {}

    private final AgentPlatform platform;
    private final CloudRemediationAgent agent;

    public IncidentInvestigationService(AgentPlatform platform, CloudRemediationAgent agent) {
        this.platform = platform;
        this.agent = agent;
    }

    public AgentProcessResult<RemediationPlan> run(String alertText) {
        if (alertText == null || alertText.isBlank()) throw new IllegalArgumentException("alert text is required");
        return platform.run(agent, new UserInput(alertText));
    }

    public Investigation investigate(String alertText) {
        AgentProcessResult<RemediationPlan> r = run(alertText);
        List<LlmCall> calls = r.llmInvocations().stream().map(IncidentInvestigationService::call).toList();
        String md = r.goalAchieved() ? r.output().content()
                : "No verified plan: confidence stayed below " + CloudRemediationAgent.HIGH_CONFIDENCE + ". Escalate to on-call.";
        return new Investigation(r.goalAchieved(), r.output(), md, r.actionsRun(), r.trace(), calls);
    }

    private static LlmCall call(LlmInvocation i) {
        return new LlmCall(i.action(), i.options().role(), i.options().temperature(), i.toolNames());
    }
}
