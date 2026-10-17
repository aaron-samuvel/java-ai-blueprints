package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/** Full app: corpus ingested at startup into SimpleVectorStore, real HTTP call, stub model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModularRagApplicationTest {

    @Autowired
    Environment env;

    @Test
    @SuppressWarnings("unchecked")
    void askEndpointReturnsBranchAndSources() {
        Map<String, Object> body = RestClient.create("http://localhost:" + env.getProperty("local.server.port"))
                .post().uri("/api/ask").header("Content-Type", "application/json")
                .body(Map.of("question", "How much sick leave do I get?"))
                .retrieve().body(Map.class);
        assertThat(body.get("intent")).isEqualTo("POLICY");
        assertThat((List<String>) body.get("sources")).contains("leave-policy.md#Sick leave");
    }
}
