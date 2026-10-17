package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.goap.platform.AgentProcessResult;

import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;

/**
 * Spring Shell command, Embabel style: {@code execute "Investigate latency spike on payment-service" -p -r}.
 * {@code -p} prints every prompt sent to a model, {@code -r} prints the plan and replanning trace.
 * Active only with the {@code shell} profile (see {@code application-shell.properties}).
 */
public class IncidentShellCommands {

    private final IncidentInvestigationService service;

    public IncidentShellCommands(IncidentInvestigationService service) {
        this.service = service;
    }

    @Command(name = "execute", description = "Investigate an alert and print the remediation plan")
    public String execute(@Argument(index = 0, description = "alert text") String alert,
                          @Option(shortName = 'p', longName = "prompts", description = "print prompts", defaultValue = "false") boolean prompts,
                          @Option(shortName = 'r', longName = "trace", description = "print the planner trace", defaultValue = "false") boolean trace) {
        return render(service.run(alert), prompts, trace);
    }

    static String render(AgentProcessResult<RemediationPlan> r, boolean prompts, boolean trace) {
        StringBuilder sb = new StringBuilder();
        if (prompts) {
            r.llmInvocations().forEach(i -> sb.append("--- prompt [").append(i.action()).append(", ").append(i.options().role())
                    .append(", temperature=").append(i.options().temperature()).append(", tools=").append(i.toolNames())
                    .append("]\n").append(i.prompt().strip()).append("\n"));
        }
        if (trace) sb.append(r.trace().stream().collect(Collectors.joining("\n", "--- trace\n", "\n")));
        sb.append(r.goalAchieved() ? r.output().content() : "No verified plan; escalate to on-call.\n");
        return sb.toString();
    }
}
