package io.github.aaronsamuvel.blueprints.hitl.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "blueprints.hitl.checkpoint=jdbc")
class SubmissionWorkflowApiTest {

    @Autowired
    Environment env;

    private RestClient http() {
        return RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
    }

    private JsonNode start(String sample) {
        return http().post().uri("/api/v1/workflows/start").header("Content-Type", "application/json")
                .body(Map.of("submissionId", "API-" + sample, "documentText", Samples.load(sample)))
                .retrieve().body(JsonNode.class);
    }

    @Test
    void threeExitsOverHttp() {
        assertThat(start("strong").get("status").asString()).isEqualTo("AUTO_APPROVED");
        assertThat(start("weak").get("status").asString()).isEqualTo("AUTO_REJECTED");

        JsonNode paused = start("mixed");
        assertThat(paused.get("status").asString()).isEqualTo("AWAITING_HUMAN");
        String id = paused.get("threadId").asString();
        assertThat(http().get().uri("/api/v1/workflows/{id}/state", id).retrieve().body(JsonNode.class)
                .get("awaitingHuman").asBoolean()).isTrue();

        JsonNode done = http().post().uri("/api/v1/workflows/{id}/resume", id).header("Content-Type", "application/json")
                .body(Map.of("approved", false, "reviewer", "lead@example.com", "comment", "needs culture evidence"))
                .retrieve().body(JsonNode.class);
        assertThat(done.get("status").asString()).isEqualTo("HUMAN_REJECTED");
        assertThat(done.get("humanVerdict").get("comment").asString()).isEqualTo("needs culture evidence");
    }

    @Test
    void errorsMapToHttpStatuses() {
        String approved = start("strong").get("threadId").asString();
        assertThatThrownBy(() -> http().post().uri("/api/v1/workflows/{id}/resume", approved).header("Content-Type", "application/json")
                .body(Map.of("approved", true)).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThatThrownBy(() -> http().get().uri("/api/v1/workflows/{id}/state", "no-such-thread").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> http().post().uri("/api/v1/workflows/start").header("Content-Type", "application/json")
                .body(Map.of("documentText", " ")).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
    }
}
