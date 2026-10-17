package io.github.aaronsamuvel.blueprints.nl2sql.a2a;

import java.util.List;
import java.util.UUID;

import io.github.aaronsamuvel.blueprints.nl2sql.agent.Nl2SqlAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.agent.Nl2SqlResult;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Exposes the NL2SQL agent to other agents: card discovery plus synchronous task execution. */
@RestController
public class A2aController {

    static final String SKILL = "nl2sql";

    private final Nl2SqlAgent agent;

    public A2aController(Nl2SqlAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/.well-known/agent.json")
    public AgentCard card() {
        return new AgentCard("nl2sql-agent",
                "Answers questions about customers and orders with guarded, read-only SQL",
                "/a2a/tasks", "0.1.0",
                List.of(new AgentCard.Skill(SKILL, "Natural language to SQL",
                        "SELECT-only, LIMIT 500, up to 3 self-healing retries",
                        List.of("How many orders did each city place?"))));
    }

    @PostMapping("/a2a/tasks")
    public A2aTask.Response run(@RequestBody A2aTask.Request req) {
        if (req.skill() != null && !SKILL.equals(req.skill())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown skill " + req.skill());
        }
        Nl2SqlResult r = agent.answer(req.message());
        String id = req.id() != null ? req.id() : UUID.randomUUID().toString();
        return new A2aTask.Response(id, r.success() ? A2aTask.State.COMPLETED : A2aTask.State.FAILED, r);
    }
}
