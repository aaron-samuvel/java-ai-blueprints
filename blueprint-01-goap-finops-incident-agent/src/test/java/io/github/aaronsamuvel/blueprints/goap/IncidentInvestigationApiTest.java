package io.github.aaronsamuvel.blueprints.goap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aaronsamuvel.blueprints.goap.incident.IncidentInvestigationService;
import io.github.aaronsamuvel.blueprints.goap.incident.IncidentShellCommands;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/** Whole application, offline (stub models): the REST endpoint and the shell command's output. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IncidentInvestigationApiTest {

    @Autowired Environment env;
    @Autowired IncidentInvestigationService service;

    @Test
    void investigateEndpointReturnsVerifiedPlanWithReplanningTrace() {
        JsonNode r = RestClient.create("http://localhost:" + env.getProperty("local.server.port"))
                .post().uri("/api/v1/incidents/investigate").contentType(MediaType.APPLICATION_JSON)
                .body("{\"alert\":\"Investigate latency spike on payment-service: p99 2950 ms over 500 ms\"}")
                .retrieve().body(JsonNode.class);
        assertThat(r.get("goalAchieved").asBoolean()).isTrue();
        assertThat(r.get("plan").get("incidentId").asString()).startsWith("INC-payment-service-");
        assertThat(r.get("actionsRun").get(3).asString()).isEqualTo("deepLogTraceAnalysis");
        assertThat(r.get("llmCalls").get(0).get("action").asString()).isEqualTo("parseAlert");
        assertThat(r.get("llmCalls").get(0).get("temperature").asDouble()).isEqualTo(0.0);
        assertThat(r.get("llmCalls").get(1).get("tools").get(0).asString()).isEqualTo("searchRunbooks");
    }

    @Test
    void shellCommandOutputShowsPromptsAndTraceWhenAsked() {
        String out = IncidentShellCommandsAccess.render(service, "Investigate latency spike on payment-service: p99 2950 ms over 500 ms");
        assertThat(out).contains("--- prompt [parseAlert, fast, temperature=0.0, tools=[]]")
                .contains("--- trace").contains("act=deepLogTraceAnalysis").contains("## Remediation plan INC-payment-service-");
    }

    /** The shell bean only exists under the "shell" profile; exercise the same code path directly. */
    static final class IncidentShellCommandsAccess {
        static String render(IncidentInvestigationService s, String alert) {
            return new IncidentShellCommands(s).execute(alert, true, true);
        }
    }
}
