package io.github.aaronsamuvel.blueprints.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.mcp.mesh.McpMesh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** One node (billing toolset) on a random port, validating test-minted JWTs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecuredNodeTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("blueprints.security.dev-jwt-secret", () -> TestJwts.SECRET);
        r.add("blueprints.mcp.toolset", () -> "billing");
    }

    @Autowired
    Environment env;

    private String base() {
        return "http://localhost:" + env.getProperty("local.server.port");
    }

    private Map<?, ?> ask(String token, String task) {
        return RestClient.create(base()).post().uri("/api/switchboard/ask")
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .body(Map.of("task", task, "message", "hello")).retrieve().body(Map.class);
    }

    @Test
    void mcpClientWithValidTokenListsAndCallsTools() {
        try (McpMesh mesh = new McpMesh(Map.of("billing", base()), () -> TestJwts.token("mcp.tools"))) {
            assertThat(mesh.tools()).extracting(t -> t.getToolDefinition().name())
                    .anyMatch(n -> n.endsWith("monthToDateCost")).anyMatch(n -> n.endsWith("listServices"));
            assertThat(mesh.tool("monthToDateCost").orElseThrow().call("{\"service\":\"search-api\"}"))
                    .contains("INR 61500");
        }
    }

    @Test
    void mcpEndpointRejectsMissingWrongScopeExpiredAndForgedTokens() {
        RestClient http = RestClient.create(base());
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";
        assertThatThrownBy(() -> http.post().uri("/mcp").header("Content-Type", "application/json").body(body).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        assertThatThrownBy(() -> http.post().uri("/mcp").header("Authorization", "Bearer " + TestJwts.token("models.use"))
                .header("Content-Type", "application/json").body(body).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        String expired = TestJwts.token("mcp.tools", TestJwts.SECRET, Instant.now().minusSeconds(600));
        String forged = TestJwts.token("mcp.tools", TestJwts.SECRET + "-attacker-key-that-is-long-enough", Instant.now().plusSeconds(300));
        for (String bad : new String[] {expired, forged}) {
            assertThatThrownBy(() -> http.post().uri("/mcp").header("Authorization", "Bearer " + bad)
                    .header("Content-Type", "application/json").body(body).retrieve().toBodilessEntity())
                    .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        }
    }

    @Test
    void mcpClientWithoutRequiredScopeCannotConnect() {
        assertThatThrownBy(() -> new McpMesh(Map.of("billing", base()), () -> TestJwts.token("models.use")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void switchboardHonoursPremiumScopeFromJwt() {
        assertThat(ask(TestJwts.token("models.use"), "CHAT").get("used")).isEqualTo("LOCAL_FAST");
        Map<?, ?> downgraded = ask(TestJwts.token("models.use"), "CRITICAL");
        assertThat(downgraded.get("used")).isEqualTo("LOCAL_SMART");
        assertThat(downgraded.get("downgraded")).isEqualTo(true);
        Map<?, ?> premium = ask(TestJwts.token("models.use models.premium"), "CRITICAL");
        assertThat(premium.get("used")).isEqualTo("PREMIUM");
        assertThat((String) premium.get("answer")).startsWith("[PREMIUM]");
    }
}
