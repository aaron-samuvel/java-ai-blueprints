package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aaronsamuvel.blueprints.nl2sql.workflow.AgentStep;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ContextCompactor;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ParallelAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.SequentialAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.WorkflowContext;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Coordinator in the JManus style: discovers an agent for an intent in the registry, fans the
 * sub-questions out to it over A2A JSON-RPC in a {@link ParallelAgent}, and collects the reports in
 * order. The whole run is a {@link SequentialAgent} with a tool-call budget.
 */
public class MeshCoordinator {

    public record SubAnswer(String question, String agent, String state, Object report) {}

    public record MeshAnswer(String intent, List<String> candidates, List<SubAnswer> answers, int toolCalls) {}

    private final AgentRegistry registry;
    private final RestClient http = RestClient.create();
    private final ContextCompactor compactor;

    public MeshCoordinator(AgentRegistry registry, ContextCompactor compactor) {
        this.registry = registry;
        this.compactor = compactor;
    }

    @SuppressWarnings("unchecked")
    public MeshAnswer research(String tenantId, String intent, List<String> questions) {
        if (questions == null || questions.isEmpty() || questions.size() > 8) throw new IllegalArgumentException("1 to 8 questions");
        WorkflowContext ctx = new WorkflowContext(questions.size() + 2);
        Map<Integer, SubAnswer> answers = new ConcurrentHashMap<>();
        AgentStep discover = AgentStep.named("discover", c -> {
            List<MeshAgentCard> found = registry.discover(intent);
            if (found.isEmpty()) throw new IllegalStateException("no agent registered for intent " + intent);
            c.put("candidates", found.stream().map(MeshAgentCard::name).toList());
            c.put("agent", found.get(0));
        });
        AgentStep fanOut = c -> {
            MeshAgentCard agent = c.require("agent");
            AgentStep[] branches = new AgentStep[questions.size()];
            for (int i = 0; i < questions.size(); i++) {
                int idx = i;
                branches[i] = b -> {
                    b.countToolCall("a2a " + agent.name());
                    Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", idx, "method", "message/send",
                            "params", Map.of("message", Map.of("role", "user", "parts", List.of(Map.of("kind", "text", "text", questions.get(idx)))),
                                    "metadata", Map.of("tenantId", tenantId)));
                    Map<String, Object> res = http.post().uri(agent.url()).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
                    Map<String, Object> result = (Map<String, Object>) res.get("result");
                    if (result == null) {
                        answers.put(idx, new SubAnswer(questions.get(idx), agent.name(), "error", res.get("error")));
                        return;
                    }
                    Object state = ((Map<String, Object>) result.get("status")).get("state");
                    Object data = ((Map<String, Object>) ((List<Map<String, Object>>) ((List<Map<String, Object>>) result.get("artifacts")).get(0).get("parts")).get(0)).get("data");
                    Object report = data instanceof Map<?, ?> d ? d.get("report") : null;
                    answers.put(idx, new SubAnswer(questions.get(idx), agent.name(), String.valueOf(state), report));
                    b.log("answered: " + questions.get(idx) + " -> " + state);
                };
            }
            new ParallelAgent("fan-out", Duration.ofSeconds(30), branches).run(c);
        };
        new SequentialAgent("mesh-research", compactor, discover, fanOut).run(ctx);
        List<SubAnswer> ordered = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) ordered.add(answers.get(i));
        return new MeshAnswer(intent, ctx.require("candidates"), ordered, ctx.toolCalls());
    }
}
