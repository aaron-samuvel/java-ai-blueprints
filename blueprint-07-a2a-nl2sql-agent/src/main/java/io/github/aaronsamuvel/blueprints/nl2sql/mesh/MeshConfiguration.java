package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ContextCompactor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Local registry (Nacos substitute), self-registration once the port is known, and the coordinator API. */
@Configuration(proxyBeanMethods = false)
public class MeshConfiguration {

    @Bean
    SlaTracker slaTracker() {
        return new SlaTracker();
    }

    @Bean
    MeshCardFactory meshCardFactory(SlaTracker sla) {
        return new MeshCardFactory(sla);
    }

    @Bean
    AgentRegistry agentRegistry(@Value("${blueprints.mesh.registry-ttl:60s}") Duration ttl) {
        return new LocalAgentRegistry(ttl, Clock.systemUTC());
    }

    @Bean
    ApplicationListener<WebServerInitializedEvent> selfRegistration(MeshCardFactory cards, AgentRegistry registry) {
        return e -> {
            cards.baseUrl("http://localhost:" + e.getWebServer().getPort());
            registry.register(cards.self());
        };
    }

    @Bean
    MeshCoordinator meshCoordinator(AgentRegistry registry) {
        return new MeshCoordinator(registry, new ContextCompactor(800, 4));
    }

    @RestController
    static class MeshController {
        record ResearchRequest(String intent, List<String> questions) {}

        private final MeshCoordinator coordinator;

        MeshController(MeshCoordinator coordinator) {
            this.coordinator = coordinator;
        }

        @PostMapping("/api/v1/mesh/research")
        MeshCoordinator.MeshAnswer research(@RequestHeader("X-Tenant-Id") String tenant, @RequestBody ResearchRequest r) {
            try {
                return coordinator.research(tenant, r.intent() == null ? A2aJsonRpcController.INTENT : r.intent(), r.questions());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
    }
}
