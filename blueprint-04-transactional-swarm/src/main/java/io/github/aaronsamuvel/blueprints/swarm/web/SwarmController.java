package io.github.aaronsamuvel.blueprints.swarm.web;

import io.github.aaronsamuvel.blueprints.swarm.SwarmCoordinator;
import io.github.aaronsamuvel.blueprints.swarm.SwarmResult;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo edge: the tenant comes from the {@code X-Tenant-Id} header. In production it would come
 * from the authenticated principal (a JWT claim), never from a client-controlled header.
 */
@RestController
public class SwarmController {

    public record SwarmRequest(String message) {}

    private final SwarmCoordinator coordinator;

    public SwarmController(SwarmCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping("/api/conversations/{conversationId}/messages")
    public SwarmResult post(@RequestHeader("X-Tenant-Id") String tenant, @PathVariable String conversationId,
                            @RequestBody SwarmRequest req) {
        return TenantContext.callAs(tenant, () -> coordinator.handle(conversationId, req.message()));
    }
}
