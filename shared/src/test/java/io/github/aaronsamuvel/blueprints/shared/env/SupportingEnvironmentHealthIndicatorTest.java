package io.github.aaronsamuvel.blueprints.shared.env;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;
import io.github.aaronsamuvel.blueprints.shared.env.EnvironmentProbe.ProbeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.health.contributor.Status;

class SupportingEnvironmentHealthIndicatorTest {

    HttpServer server;
    String base;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        server.createContext("/mcp", ex -> { ex.sendResponseHeaders(401, -1); ex.close(); });
        server.createContext("/api/tags", ex -> { ex.sendResponseHeaders(200, -1); ex.close(); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void upWhenEveryConfiguredProbePasses() {
        StubEmbeddingModel emb = new StubEmbeddingModel(16);
        var indicator = new SupportingEnvironmentHealthIndicator(List.of(
                EnvironmentProbes.chatModel(StubChatModel::echo, null),
                EnvironmentProbes.embeddingModel(() -> emb),
                new VectorStoreProbe(() -> SimpleVectorStore.builder(emb).build()),
                EnvironmentProbes.httpHealth("presidio", base + "/health"),
                EnvironmentProbes.mcpServers(List.of(base + "/mcp")).get(0)));
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKeys("chatModel", "embeddingModel", "vectorStore", "presidio", "mcp " + base + "/mcp");
        assertThat(health.getDetails().get("embeddingModel").toString()).contains("16 dimensions");
        // a secured MCP endpoint answering 401 still counts as reachable
        assertThat(health.getDetails().get("mcp " + base + "/mcp").toString()).startsWith("UP").contains("HTTP 401");
    }

    @Test
    void downWhenAConfiguredServiceIsUnreachableButNotForUnconfiguredOnes() {
        var indicator = new SupportingEnvironmentHealthIndicator(List.of(
                EnvironmentProbes.httpHealth("presidio", "http://127.0.0.1:1/health"),
                EnvironmentProbes.httpHealth("other", "")));
        var results = indicator.runProbes();
        assertThat(results).extracting(ProbeResult::state)
                .containsExactly(ProbeResult.State.DOWN, ProbeResult.State.NOT_CONFIGURED);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(SupportingEnvironmentHealthIndicator.table(results)).contains("COMPONENT").contains("NOT_CONFIGURED");
    }

    @Test
    void unconfiguredOnlyIsUp() {
        var indicator = new SupportingEnvironmentHealthIndicator(List.of(
                EnvironmentProbes.chatModel(() -> null, null), EnvironmentProbes.mcpServers(List.of("")).get(0)));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }
}
