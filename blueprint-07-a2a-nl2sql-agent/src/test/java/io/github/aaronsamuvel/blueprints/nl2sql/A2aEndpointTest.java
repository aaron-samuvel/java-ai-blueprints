package io.github.aaronsamuvel.blueprints.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

/** Full app over HTTP: H2 initialised from schema.sql/data.sql, stub model that always writes the same SQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class A2aEndpointTest {

    @TestConfiguration
    static class Model {
        @Bean
        @Primary
        StubChatModel chatModel() {
            return StubChatModel.fixed("SELECT name FROM customers WHERE city = 'Chennai' ORDER BY name");
        }
    }

    @Autowired
    Environment env;

    private RestClient http() {
        return RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void agentCardIsDiscoverable() {
        Map<String, Object> card = http().get().uri("/.well-known/agent.json").retrieve().body(Map.class);
        assertThat(card.get("name")).isEqualTo("nl2sql-agent");
        assertThat((List<Map<String, Object>>) card.get("skills")).extracting(s -> s.get("id")).containsExactly("nl2sql");
    }

    @Test
    @SuppressWarnings("unchecked")
    void taskRunsThroughGuardAndReturnsRows() {
        Map<String, Object> res = http().post().uri("/a2a/tasks").header("Content-Type", "application/json")
                .body(Map.of("id", "t-1", "skill", "nl2sql", "message", "Who lives in Chennai?"))
                .retrieve().body(Map.class);
        assertThat(res.get("id")).isEqualTo("t-1");
        assertThat(res.get("state")).isEqualTo("COMPLETED");
        Map<String, Object> artifact = (Map<String, Object>) res.get("artifact");
        assertThat((String) artifact.get("sql")).endsWith("LIMIT 500");
        assertThat((List<Map<String, Object>>) artifact.get("rows")).extracting(r -> r.get("NAME"))
                .containsExactly("Arjun Das", "Asha Rao");
    }
}
