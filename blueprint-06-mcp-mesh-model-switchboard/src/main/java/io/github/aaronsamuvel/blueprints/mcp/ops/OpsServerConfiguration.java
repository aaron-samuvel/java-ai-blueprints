package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

import io.modelcontextprotocol.server.McpSyncServer;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * The "mcp-ops-server" role of a node ({@code blueprints.mcp.toolset=ops}): Spring Batch
 * introspection, JVM diagnostics and the runtime OpenAPI bridge, all on the node's secured MCP endpoint.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "blueprints.mcp.toolset", havingValue = "ops")
public class OpsServerConfiguration {

    @Bean
    JfrEventMonitor jfrEventMonitor(@Value("${blueprints.ops.jfr.pinned-threshold:20ms}") Duration threshold) {
        return new JfrEventMonitor(threshold);
    }

    @Bean
    ToolCallbackProvider opsTools(JobRepository repository, JobOperator operator, JfrEventMonitor jfr, @Lazy HealthEndpoint health,
                                  @Value("${blueprints.ops.batch.restartable-jobs:" + LedgerReconciliationJobConfiguration.JOB + "}") List<String> restartable,
                                  @Value("${blueprints.ops.batch.max-restarts:3}") int maxRestarts) {
        return MethodToolCallbackProvider.builder().toolObjects(
                new BatchIntrospectionMcpTools(repository, operator, new HashSet<>(restartable), maxRestarts, 12),
                new JvmDiagnosticsMcpTools(jfr, health)).build();
    }

    @Bean
    DynamicOpenApiMcpBridge dynamicOpenApiMcpBridge(McpSyncServer server) {
        return new DynamicOpenApiMcpBridge(server);
    }
}
