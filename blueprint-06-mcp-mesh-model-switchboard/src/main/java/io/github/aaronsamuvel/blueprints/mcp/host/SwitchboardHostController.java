package io.github.aaronsamuvel.blueprints.mcp.host;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Host API for the router; it sits under the switchboard path, so it needs scope {@code models.use}. */
@RestController
@RequestMapping("/api/switchboard/v2")
public class SwitchboardHostController {

    public record RouteRequest(String prompt, Boolean privacySensitive, Long latencyBudgetMs) {
        TaskProfile profile() {
            return new TaskProfile(prompt, Boolean.TRUE.equals(privacySensitive),
                    latencyBudgetMs == null ? null : Duration.ofMillis(latencyBudgetMs));
        }
    }

    private final ModelSwitchboardRouter router;

    public SwitchboardHostController(ModelSwitchboardRouter router) {
        this.router = router;
    }

    @PostMapping("/route")
    public ModelSwitchboardRouter.RoutedAnswer route(@RequestBody RouteRequest r) {
        try {
            return router.ask(r.profile());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/extract-ticket")
    public TicketExtraction extract(@RequestBody RouteRequest r) {
        try {
            return router.extract(r.profile(), TicketExtraction.class, 3);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
