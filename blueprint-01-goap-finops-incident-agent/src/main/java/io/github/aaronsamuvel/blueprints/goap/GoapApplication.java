package io.github.aaronsamuvel.blueprints.goap;

import java.math.BigDecimal;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.goap.incident.CloudRemediationAgent;
import io.github.aaronsamuvel.blueprints.goap.incident.DemoIncidentModel;
import io.github.aaronsamuvel.blueprints.goap.incident.InMemoryTelemetry;
import io.github.aaronsamuvel.blueprints.goap.incident.IncidentInvestigationService;
import io.github.aaronsamuvel.blueprints.goap.incident.IncidentShellCommands;
import io.github.aaronsamuvel.blueprints.goap.incident.RunbookSearchTools;
import io.github.aaronsamuvel.blueprints.goap.ooda.FinOpsIncidentAgent;
import io.github.aaronsamuvel.blueprints.goap.platform.AgentPlatform;
import io.github.aaronsamuvel.blueprints.goap.platform.LlmOptions;
import io.github.aaronsamuvel.blueprints.goap.platform.ToolGroups;
import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Import(StubModelConfiguration.class)
public class GoapApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoapApplication.class, args);
    }

    /** The original cost-spike agent (billing CSV), kept at /api/incidents/run. */
    @Bean
    FinOpsIncidentAgent finOpsIncidentAgent(ChatModel model,
                                            @Value("${blueprints.goap.anomaly-factor:1.5}") BigDecimal factor,
                                            @Value("${blueprints.goap.auto-approve-limit-inr:10000}") BigDecimal limit) {
        return new FinOpsIncidentAgent(model, factor, limit, 10);
    }

    // --- Handbook Blueprint 1: CloudRemediationAgent on the GOAP agent platform ---

    @Bean
    InMemoryTelemetry telemetry() {
        return new InMemoryTelemetry();
    }

    @Bean
    CloudRemediationAgent cloudRemediationAgent(InMemoryTelemetry telemetry,
                                                @Value("${blueprints.goap.fast-model:}") String fastModel,
                                                @Value("${blueprints.goap.frontier-model:}") String frontierModel) {
        LlmOptions fast = fastModel.isBlank() ? LlmOptions.fast() : LlmOptions.fast().withModel(fastModel);
        LlmOptions frontier = frontierModel.isBlank() ? LlmOptions.frontier() : LlmOptions.frontier().withModel(frontierModel);
        return new CloudRemediationAgent(telemetry, telemetry, fast, frontier);
    }

    /**
     * Offline ({@code blueprints.model=stub}) both roles use the deterministic {@link DemoIncidentModel};
     * otherwise both use the configured ChatModel and the role's model name picks the provider model.
     */
    @Bean
    AgentPlatform incidentAgentPlatform(ChatModel chatModel, @Value("${blueprints.model:stub}") String mode) {
        ChatModel m = "stub".equals(mode) ? DemoIncidentModel.create() : chatModel;
        return new AgentPlatform(Map.of(LlmOptions.FAST, m, LlmOptions.FRONTIER, m),
                ToolGroups.of(CloudRemediationAgent.RUNBOOK_SEARCH, new RunbookSearchTools()), 12);
    }

    @Bean
    IncidentInvestigationService incidentInvestigationService(AgentPlatform platform, CloudRemediationAgent agent) {
        return new IncidentInvestigationService(platform, agent);
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("shell")
    static class ShellConfiguration {
        @Bean
        IncidentShellCommands incidentShellCommands(IncidentInvestigationService service) {
            return new IncidentShellCommands(service);
        }
    }
}
