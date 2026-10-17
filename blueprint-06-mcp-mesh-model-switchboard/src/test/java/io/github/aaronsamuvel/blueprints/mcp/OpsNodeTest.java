package io.github.aaronsamuvel.blueprints.mcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;
import io.github.aaronsamuvel.blueprints.mcp.mesh.McpMesh;
import io.github.aaronsamuvel.blueprints.mcp.ops.LedgerReconciliationJobConfiguration.UpstreamFeed;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The node in its mcp-ops-server role (toolset=ops), on a random port with test-minted JWTs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpsNodeTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("blueprints.security.dev-jwt-secret", () -> TestJwts.SECRET);
        r.add("blueprints.mcp.toolset", () -> "ops");
        r.add("spring.datasource.url", () -> "jdbc:h2:mem:opstest;DB_CLOSE_DELAY=-1");
    }

    @Autowired Environment env;
    @Autowired JobOperator jobOperator;
    @Autowired Job ledgerReconciliationJob;
    @Autowired UpstreamFeed feed;

    private String base() {
        return "http://localhost:" + env.getProperty("local.server.port");
    }

    private static String call(McpMesh mesh, String tool, String json) {
        return mesh.tool(tool).orElseThrow(() -> new AssertionError("no tool " + tool)).call(json);
    }

    @Test
    void opsToolsAreDiscoverableOverTheSecuredMcpEndpoint() {
        try (McpMesh mesh = new McpMesh(Map.of("ops", base()), () -> TestJwts.token("mcp.tools"))) {
            assertThat(mesh.tools()).extracting(t -> t.getToolDefinition().name())
                    .anyMatch(n -> n.endsWith("listJobExecutions")).anyMatch(n -> n.endsWith("failedStepDetails"))
                    .anyMatch(n -> n.endsWith("restartFailedJob")).anyMatch(n -> n.endsWith("heapMemoryPools"))
                    .anyMatch(n -> n.endsWith("gcPauses")).anyMatch(n -> n.endsWith("virtualThreadPinnedEvents"))
                    .anyMatch(n -> n.endsWith("actuatorHealth"));
        }
    }

    @Test
    void failedBatchJobIsInspectedAndRestartedOnlyThroughTheGuardrails() throws Exception {
        feed.markBroken(true);
        var failed = jobOperator.start(ledgerReconciliationJob, new JobParametersBuilder().addString("businessDate", "2026-10-16").toJobParameters());
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        long id = failed.getId();

        try (McpMesh mesh = new McpMesh(Map.of("ops", base()), () -> TestJwts.token("mcp.tools"))) {
            assertThat(call(mesh, "listJobExecutions", "{\"jobName\":\"ledgerReconciliationJob\"}"))
                    .contains("execution " + id).contains("FAILED").contains("reconcile=FAILED").contains("loadLedger=COMPLETED");
            assertThat(call(mesh, "failedStepDetails", "{\"executionId\":" + id + "}"))
                    .contains("step reconcile FAILED").contains("HTTP 503");
            assertThat(call(mesh, "restartFailedJob", "{\"executionId\":" + id + ",\"reason\":\"retry\"}"))
                    .contains("REFUSED").contains("at least 15 characters");
            assertThat(call(mesh, "restartFailedJob", "{\"executionId\":" + id + ",\"reason\":\"upstream feed recovered at 09:10\"}"))
                    .contains("DRY RUN OK");

            feed.markBroken(false);
            String restarted = call(mesh, "restartFailedJob", "{\"executionId\":" + id + ",\"reason\":\"upstream feed recovered at 09:10\",\"dryRun\":false}");
            assertThat(restarted).contains("RESTARTED").contains("COMPLETED");
            assertThat(call(mesh, "restartFailedJob", "{\"executionId\":" + id + ",\"reason\":\"upstream feed recovered at 09:10\",\"dryRun\":false}"))
                    .contains("REFUSED").contains("is newer for the same instance");
        }
    }

    @Test
    void jvmDiagnosticsReportHeapHealthAndPinnedVirtualThreads() throws Exception {
        Object lock = new Object();
        Thread vt = Thread.ofVirtual().start(() -> {
            synchronized (lock) {
                try {
                    Thread.sleep(150); // blocking while holding a monitor pins the carrier on JDK 21
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        vt.join();
        try (McpMesh mesh = new McpMesh(Map.of("ops", base()), () -> TestJwts.token("mcp.tools"))) {
            assertThat(call(mesh, "heapMemoryPools", "{}")).contains("heap:").contains("pool ");
            assertThat(call(mesh, "actuatorHealth", "{}")).contains("status UP");
            assertThat(call(mesh, "gcPauses", "{}")).contains("collections");
            String pinned = "";
            for (int i = 0; i < 40 && !pinned.contains("VirtualThreadPinned"); i++) {
                Thread.sleep(250);
                pinned = call(mesh, "virtualThreadPinnedEvents", "{}");
            }
            assertThat(pinned).contains("VirtualThreadPinned").contains("OpsNodeTest");
        }
    }

    @Test
    void openApiBridgeRegistersToolsAtRuntimeAndNotifiesConnectedClients() throws IOException, InterruptedException {
        HttpServer backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/items/", ex -> {
            byte[] body = ("{\"id\":\"" + ex.getRequestURI().getPath().substring(7) + "\",\"name\":\"Ergo Mouse\",\"stock\":4}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        backend.start();
        String spec = """
                {"openapi":"3.0.3","info":{"title":"catalog","version":"1"},
                 "paths":{"/items/{id}":{"get":{"operationId":"getItem","summary":"Get one catalogue item",
                   "parameters":[{"name":"id","in":"path","required":true,"schema":{"type":"string"}}]},
                   "delete":{"operationId":"deleteItem"}}}}""";
        List<List<String>> notifications = new CopyOnWriteArrayList<>();
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(base())
                .httpRequestCustomizer((b, m, u, body, ctx) -> b.header("Authorization", "Bearer " + TestJwts.token("mcp.tools"))).build();
        McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10))
                .toolsChangeConsumer(tools -> notifications.add(tools.stream().map(McpSchema.Tool::name).toList())).build();
        try {
            client.initialize();
            RestClient http = RestClient.create(base());
            Map<String, Object> req = Map.of("source", "catalog", "baseUrl", "http://127.0.0.1:" + backend.getAddress().getPort(), "openApiJson", spec);
            assertThatThrownBy(() -> http.post().uri("/api/ops/openapi-bridge").header("Authorization", "Bearer " + TestJwts.token("mcp.tools"))
                    .contentType(MediaType.APPLICATION_JSON).body(req).retrieve().toBodilessEntity())
                    .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));

            Map<?, ?> reg = http.post().uri("/api/ops/openapi-bridge").header("Authorization", "Bearer " + TestJwts.token("mcp.admin"))
                    .contentType(MediaType.APPLICATION_JSON).body(req).retrieve().body(Map.class);
            assertThat(reg.get("registered").toString()).isEqualTo("[catalog_getItem]");
            assertThat(reg.get("skipped").toString()).contains("DELETE /items/{id}");

            for (int i = 0; i < 40 && notifications.stream().noneMatch(n -> n.contains("catalog_getItem")); i++) Thread.sleep(100);
            assertThat(notifications).anyMatch(n -> n.contains("catalog_getItem"));
            assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).contains("catalog_getItem");
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("catalog_getItem", Map.of("id", "SKU-7")));
            assertThat(((McpSchema.TextContent) result.content().get(0)).text()).contains("\"id\":\"SKU-7\"").contains("Ergo Mouse");

            int before = notifications.size();
            http.delete().uri("/api/ops/openapi-bridge/catalog").header("Authorization", "Bearer " + TestJwts.token("mcp.admin")).retrieve().toBodilessEntity();
            for (int i = 0; i < 40 && notifications.size() == before; i++) Thread.sleep(100);
            assertThat(notifications.get(notifications.size() - 1)).doesNotContain("catalog_getItem");
        } finally {
            client.closeGracefully();
            backend.stop(0);
        }
    }

    @Test
    void healthIsPublicAndTheRouterApiNeedsAToken() {
        RestClient http = RestClient.create(base());
        assertThat(http.get().uri("/actuator/health").retrieve().body(String.class)).contains("UP");
        assertThatThrownBy(() -> http.post().uri("/api/switchboard/v2/route").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prompt", "hi")).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpClientErrorException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        Map<?, ?> routed = http.post().uri("/api/switchboard/v2/route").header("Authorization", "Bearer " + TestJwts.token("models.use"))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("prompt", "My PAN is ABCDE1234F, what is my tax slab?"))
                .retrieve().body(Map.class);
        assertThat(routed.get("used")).isEqualTo("LOCAL_SOVEREIGN");
        assertThat(routed.get("answer").toString()).startsWith("[LOCAL_SOVEREIGN]");
        Map<?, ?> ticket = http.post().uri("/api/switchboard/v2/extract-ticket").header("Authorization", "Bearer " + TestJwts.token("models.use"))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("prompt", "I was charged twice for my plan this month", "latencyBudgetMs", 800))
                .retrieve().body(Map.class);
        assertThat(ticket.get("category")).isEqualTo("billing");
    }
}
