package io.github.aaronsamuvel.blueprints.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.mcp.mesh.McpMesh;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Two real nodes (billing + inventory) on random ports; one mesh client merges their tools. */
class McpMeshTest {

    static ConfigurableApplicationContext billing;
    static ConfigurableApplicationContext inventory;

    static ConfigurableApplicationContext start(String toolset) {
        return new SpringApplicationBuilder(McpMeshApplication.class).run(
                "--server.port=0", "--blueprints.mcp.toolset=" + toolset,
                "--blueprints.security.dev-jwt-secret=" + TestJwts.SECRET, "--spring.main.banner-mode=off");
    }

    static String url(ConfigurableApplicationContext ctx) {
        return "http://localhost:" + ctx.getEnvironment().getProperty("local.server.port");
    }

    @BeforeAll
    static void up() {
        billing = start("billing");
        inventory = start("inventory");
    }

    @AfterAll
    static void down() {
        billing.close();
        inventory.close();
    }

    @Test
    void meshMergesToolsFromBothNodesAndCallsEach() {
        Map<String, String> nodes = new LinkedHashMap<>();
        nodes.put("billing", url(billing));
        nodes.put("inventory", url(inventory));
        try (McpMesh mesh = new McpMesh(nodes, () -> TestJwts.token("mcp.tools"))) {
            List<String> names = mesh.tools().stream().map(t -> t.getToolDefinition().name()).toList();
            assertThat(names).hasSize(3)
                    .anyMatch(n -> n.endsWith("monthToDateCost")).anyMatch(n -> n.endsWith("stockLevel"));
            assertThat(mesh.tool("stockLevel").orElseThrow().call("{\"sku\":\"SKU-100\"}")).contains("in stock: 42");
            assertThat(mesh.tool("listServices").orElseThrow().call("{}")).contains("batch-etl");
        }
    }

    @Test
    void meshToolsPlugIntoChatClient() {
        try (McpMesh mesh = new McpMesh(Map.of("inventory", url(inventory)), () -> TestJwts.token("mcp.tools"))) {
            StubChatModel model = StubChatModel.fixed("ok");
            String answer = ChatClient.create(model).prompt().user("stock of SKU-100?")
                    .toolCallbacks(mesh.tools()).call().content();
            assertThat(answer).isEqualTo("ok");
            // the tool definitions from the remote node were offered to the model
            assertThat(model.lastPrompt().getOptions()).isNotNull();
        }
    }
}
