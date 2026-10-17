package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code POST /api/v1/data-agent/ask}. The demo takes the tenant from {@code X-Tenant-Id}; in
 * production it must come from the authenticated principal.
 */
@RestController
@RequestMapping("/api/v1/data-agent")
public class DataAgentController {

    public record AskRequest(String question) {}

    private final DataAgentService service;

    public DataAgentController(DataAgentService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    public DataAgentService.DataAgentResponse ask(@RequestHeader("X-Tenant-Id") String tenant, @RequestBody AskRequest r) {
        try {
            return service.ask(tenant, r.question());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
