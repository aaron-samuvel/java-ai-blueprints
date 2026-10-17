package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin API for the OpenAPI bridge; needs scope {@code mcp.admin} (see SecurityConfiguration). */
@RestController
@RequestMapping("/api/ops/openapi-bridge")
public class OpsBridgeController {

    public record RegisterRequest(String source, String baseUrl, String openApiJson) {}

    private final ObjectProvider<DynamicOpenApiMcpBridge> bridge;

    public OpsBridgeController(ObjectProvider<DynamicOpenApiMcpBridge> bridge) {
        this.bridge = bridge;
    }

    private DynamicOpenApiMcpBridge bridge() {
        DynamicOpenApiMcpBridge b = bridge.getIfAvailable();
        if (b == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "the OpenAPI bridge runs only on the ops node (MCP_TOOLSET=ops)");
        return b;
    }

    @PostMapping
    public DynamicOpenApiMcpBridge.Registration register(@RequestBody RegisterRequest r) {
        try {
            return bridge().register(r.source(), r.openApiJson(), r.baseUrl());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{source}")
    public List<String> unregister(@PathVariable String source) {
        return bridge().unregister(source);
    }
}
