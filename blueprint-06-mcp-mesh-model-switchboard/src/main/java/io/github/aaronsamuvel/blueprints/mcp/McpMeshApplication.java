package io.github.aaronsamuvel.blueprints.mcp;

import java.util.Map;

import io.github.aaronsamuvel.blueprints.mcp.switchboard.ModelSwitchboard;
import io.github.aaronsamuvel.blueprints.mcp.switchboard.ModelTier;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * One mesh node: an MCP tool server (toolset chosen by property) plus the model switchboard API.
 * Offline, each tier is a stub that prefixes its tier name so routing is visible.
 */
@SpringBootApplication
public class McpMeshApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpMeshApplication.class, args);
    }

    @Bean
    ModelSwitchboard modelSwitchboard() {
        return new ModelSwitchboard(Map.of(
                ModelTier.LOCAL_FAST, new StubChatModel(p -> "[LOCAL_FAST] " + StubChatModel.lastText(p)),
                ModelTier.LOCAL_SMART, new StubChatModel(p -> "[LOCAL_SMART] " + StubChatModel.lastText(p)),
                ModelTier.PREMIUM, new StubChatModel(p -> "[PREMIUM] " + StubChatModel.lastText(p))));
    }
}
