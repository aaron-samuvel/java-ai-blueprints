package io.github.aaronsamuvel.blueprints.swarm.commerce.web;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.aaronsamuvel.blueprints.swarm.commerce.AgentOrchestratorService;
import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantSessionKey;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code POST /api/v1/swarm/chat} (JSON), {@code POST /api/v1/swarm/chat/stream} (Server-Sent Events:
 * one {@code handoff} event per handoff badge, then the {@code reply}), and
 * {@code GET /api/v1/swarm/sessions/{sessionId}/history}.
 * Demo edge: tenant and user come from the request. In production take them from the authenticated
 * principal (JWT claims), never from client-controlled fields.
 */
@RestController
@RequestMapping("/api/v1/swarm")
public class SwarmChatController {

    public record ChatRequest(String tenantId, String userId, String sessionId, String message) {}

    private final AgentOrchestratorService orchestrator;
    private final ExecutorService streams = Executors.newVirtualThreadPerTaskExecutor();

    public SwarmChatController(AgentOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    private static TenantSessionKey key(String tenant, String user, String session) {
        try {
            return new TenantSessionKey(tenant, user, session);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/chat")
    public AgentOrchestratorService.SwarmReply chat(@RequestBody ChatRequest r) {
        try {
            return orchestrator.chat(key(r.tenantId(), r.userId(), r.sessionId()), r.message());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(path = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest r) {
        TenantSessionKey key = key(r.tenantId(), r.userId(), r.sessionId());
        SseEmitter emitter = new SseEmitter(120_000L);
        streams.submit(() -> {
            try {
                var reply = orchestrator.chat(key, r.message(), handoff -> {
                    try {
                        emitter.send(SseEmitter.event().name("handoff").data(handoff, MediaType.APPLICATION_JSON));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                });
                emitter.send(SseEmitter.event().name("reply").data(reply, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping("/sessions/{sessionId}/history")
    public List<AgentOrchestratorService.HistoryEntry> history(@PathVariable String sessionId,
                                                               @RequestHeader("X-Tenant-Id") String tenant,
                                                               @RequestHeader("X-User-Id") String user) {
        return orchestrator.history(key(tenant, user, sessionId));
    }
}
