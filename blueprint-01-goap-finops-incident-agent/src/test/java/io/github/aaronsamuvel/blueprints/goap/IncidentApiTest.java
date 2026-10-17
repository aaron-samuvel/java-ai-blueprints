package io.github.aaronsamuvel.blueprints.goap;

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
class IncidentApiTest {

    @TestConfiguration
    static class Model {
        @Bean
        @Primary
        StubChatModel analyst() {
            return StubChatModel.fixed("{\"cause\":\"IDLE_RESOURCES\",\"explanation\":\"cpu low\"}");
        }
    }

    @Autowired
    Environment env;

    @Test
    @SuppressWarnings("unchecked")
    void postBillingCsvGetsReport() {
        String csv = """
                day,service,cost_inr,cpu
                2026-10-11,cache,100.00,0.30
                2026-10-12,cache,110.00,0.31
                2026-10-13,cache,900.00,0.05
                """;
        Map<String, Object> body = RestClient.create("http://localhost:" + env.getProperty("local.server.port"))
                .post().uri("/api/incidents/run").header("Content-Type", "text/csv").body(csv)
                .retrieve().body(Map.class);
        assertThat(body.get("outcome")).isEqualTo("RESOLVED");
        assertThat(body.get("service")).isEqualTo("cache");
    }
}
