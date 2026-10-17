package io.github.aaronsamuvel.blueprints.hitl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReviewApiTest {

    @TestConfiguration
    static class Drafter {
        @Bean
        @Primary
        StubChatModel drafter() {
            return StubChatModel.fixed("Employees get 24 days of paid annual leave per calendar year.");
        }
    }

    @Autowired
    Environment env;

    @Test
    @SuppressWarnings("unchecked")
    void startPauseApproveOverHttp() {
        RestClient http = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
        Map<String, Object> started = http.post().uri("/api/reviews").header("Content-Type", "application/json")
                .body(Map.of("question", "How much annual leave?",
                        "context", "Employees get 24 days of paid annual leave per calendar year."))
                .retrieve().body(Map.class);
        assertThat(started.get("status")).isEqualTo("AWAITING_HUMAN");
        String id = (String) started.get("threadId");

        Map<String, Object> done = http.post().uri("/api/reviews/{id}/decision", id).header("Content-Type", "application/json")
                .body(Map.of("approve", true)).retrieve().body(Map.class);
        assertThat(done.get("status")).isEqualTo("PUBLISHED");
        assertThat(http.get().uri("/api/reviews/{id}", id).retrieve().body(Map.class).get("status")).isEqualTo("PUBLISHED");
    }
}
