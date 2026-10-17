package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.aaronsamuvel.blueprints.nl2sql.dataagent.DataAgentService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A2A protocol subset over JSON-RPC 2.0 at {@code POST /a2a}: method {@code message/send} with
 * text parts, returning a {@code Task} with a data artifact. The tenant travels in
 * {@code params.metadata.tenantId}. Streaming, push notifications and {@code tasks/get} are not
 * implemented. The card is served at {@code /.well-known/agent-card.json}; the registry is under
 * {@code /a2a/registry}.
 */
@RestController
public class A2aJsonRpcController {

    public static final String INTENT = "nl2sql.analytics";

    private final DataAgentService dataAgent;
    private final AgentRegistry registry;
    private final SlaTracker sla;
    private final MeshCardFactory cards;

    public A2aJsonRpcController(DataAgentService dataAgent, AgentRegistry registry, SlaTracker sla, MeshCardFactory cards) {
        this.dataAgent = dataAgent;
        this.registry = registry;
        this.sla = sla;
        this.cards = cards;
    }

    @GetMapping("/.well-known/agent-card.json")
    public MeshAgentCard card() {
        return cards.self();
    }

    @GetMapping("/a2a/registry")
    public List<MeshAgentCard> registry(@RequestParam(required = false) String intent) {
        return intent == null ? registry.all() : registry.discover(intent);
    }

    @PostMapping("/a2a/registry")
    public MeshAgentCard register(@RequestBody MeshAgentCard card) {
        registry.register(card);
        return card;
    }

    @PostMapping("/a2a")
    @SuppressWarnings("unchecked")
    public Map<String, Object> rpc(@RequestBody Map<String, Object> req) {
        Object id = req.get("id");
        if (!"2.0".equals(req.get("jsonrpc"))) return error(id, -32600, "jsonrpc must be 2.0");
        if (!"message/send".equals(req.get("method"))) return error(id, -32601, "method not found: " + req.get("method"));
        Map<String, Object> params = req.get("params") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> message = params.get("message") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        Map<String, Object> metadata = params.get("metadata") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        String text = message.get("parts") instanceof List<?> parts ? parts.stream()
                .filter(p -> p instanceof Map<?, ?> pm && "text".equals(pm.get("kind")))
                .map(p -> String.valueOf(((Map<?, ?>) p).get("text"))).reduce((a, b) -> a + "\n" + b).orElse("") : "";
        String tenant = String.valueOf(metadata.getOrDefault("tenantId", ""));
        if (text.isBlank()) return error(id, -32602, "message needs a text part");

        long t0 = System.nanoTime();
        DataAgentService.DataAgentResponse r;
        try {
            r = dataAgent.ask(tenant, text);
        } catch (RuntimeException e) {
            sla.record((System.nanoTime() - t0) / 1_000_000, false);
            return error(id, -32603, e.getMessage());
        }
        sla.record((System.nanoTime() - t0) / 1_000_000, r.success());
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("kind", "task");
        task.put("id", UUID.randomUUID().toString());
        task.put("contextId", message.getOrDefault("contextId", UUID.randomUUID().toString()));
        task.put("status", Map.of("state", r.success() ? "completed" : "failed"));
        task.put("artifacts", List.of(Map.of("artifactId", UUID.randomUUID().toString(), "name", "data-analysis-report",
                "parts", List.of(Map.of("kind", "data", "data", r)))));
        return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "result", task);
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "error", Map.of("code", code, "message", String.valueOf(message)));
    }
}
