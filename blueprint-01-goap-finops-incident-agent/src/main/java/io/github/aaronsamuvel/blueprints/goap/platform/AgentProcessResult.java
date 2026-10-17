package io.github.aaronsamuvel.blueprints.goap.platform;

import java.util.List;

/**
 * Outcome of one agent run. {@code goalAchieved} is false when no plan reaches the goal any more
 * (for example confidence stayed low after the one allowed deep trace); then {@code output} is null.
 */
public record AgentProcessResult<T>(boolean goalAchieved, T output, List<String> actionsRun, List<String> trace,
                                    List<LlmInvocation> llmInvocations) {
    public AgentProcessResult {
        actionsRun = List.copyOf(actionsRun);
        trace = List.copyOf(trace);
        llmInvocations = List.copyOf(llmInvocations);
    }
}
