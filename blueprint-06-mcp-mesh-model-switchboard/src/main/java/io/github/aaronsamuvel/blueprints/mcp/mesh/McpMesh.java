package io.github.aaronsamuvel.blueprints.mcp.mesh;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

/**
 * Client side of the mesh: connects to several MCP servers over Streamable HTTP, attaching a bearer
 * token to every request, and merges their tools into one list of Spring AI {@link ToolCallback}s
 * that any {@code ChatClient} can use. Tokens come from a supplier so they can be refreshed.
 */
public final class McpMesh implements AutoCloseable {

    private final List<McpSyncClient> clients = new ArrayList<>();
    private final SyncMcpToolCallbackProvider provider;

    public McpMesh(Map<String, String> nodeUrls, Supplier<String> bearerToken) {
        nodeUrls.forEach((name, url) -> {
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(url)
                    .httpRequestCustomizer((builder, method, uri, body, ctx) ->
                            builder.header("Authorization", "Bearer " + bearerToken.get()))
                    .build();
            McpSyncClient c = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
            c.initialize();
            clients.add(c);
        });
        this.provider = SyncMcpToolCallbackProvider.builder().mcpClients(clients).build();
    }

    public List<ToolCallback> tools() {
        return Arrays.asList(provider.getToolCallbacks());
    }

    /** Tool whose name ends with the given name (the provider may add a prefix to avoid clashes). */
    public Optional<ToolCallback> tool(String name) {
        return tools().stream().filter(t -> t.getToolDefinition().name().endsWith(name)).findFirst();
    }

    @Override
    public void close() {
        clients.forEach(McpSyncClient::closeGracefully);
    }
}
