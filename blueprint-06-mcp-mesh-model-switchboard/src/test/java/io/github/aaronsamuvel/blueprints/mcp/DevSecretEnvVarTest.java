package io.github.aaronsamuvel.blueprints.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Regression: the README tells you to set BLUEPRINTS_DEV_JWT_SECRET, but the decoder reads
 * blueprints.security.dev-jwt-secret. Passing the env-var name as a property source exercises the
 * same placeholder in application.properties that a real environment variable resolves.
 */
class DevSecretEnvVarTest {

    @Test
    void documentedEnvVarNameConfiguresTheDevDecoder() throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(McpMeshApplication.class).run(
                "--server.port=0", "--BLUEPRINTS_DEV_JWT_SECRET=" + TestJwts.SECRET, "--spring.main.banner-mode=off")) {
            String url = "http://localhost:" + ctx.getEnvironment().getProperty("local.server.port") + "/api/switchboard/ask";
            HttpResponse<String> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + TestJwts.token("models.use"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"task\":\"CHAT\",\"message\":\"hi\"}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).contains("LOCAL_FAST");
        }
    }

    @Test
    void withoutAnySecretTheNodeRefusesToStart() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(McpMeshApplication.class)
                .run("--server.port=0", "--spring.main.banner-mode=off").close())
                .hasStackTraceContaining("JwtDecoder");
    }
}
