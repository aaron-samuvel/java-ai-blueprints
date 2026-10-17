package io.github.aaronsamuvel.blueprints.mcp.tools;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Each running instance is one node of the mesh and exposes one toolset, chosen with
 * {@code blueprints.mcp.toolset=billing|inventory}. The Spring AI MCP server starter publishes every
 * {@link ToolCallbackProvider} bean as MCP tools over Streamable HTTP at {@code /mcp}.
 */
@Configuration(proxyBeanMethods = false)
public class ToolsetConfiguration {

    @Bean
    @ConditionalOnProperty(name = "blueprints.mcp.toolset", havingValue = "billing", matchIfMissing = true)
    ToolCallbackProvider billingTools() {
        return MethodToolCallbackProvider.builder().toolObjects(new BillingTools()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "blueprints.mcp.toolset", havingValue = "inventory")
    ToolCallbackProvider inventoryTools() {
        return MethodToolCallbackProvider.builder().toolObjects(new InventoryTools()).build();
    }
}
