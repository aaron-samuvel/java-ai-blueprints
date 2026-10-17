package io.github.aaronsamuvel.blueprints.goap.incident;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentInvestigationController {

    public record InvestigateRequest(String alert) {}

    private final IncidentInvestigationService service;

    public IncidentInvestigationController(IncidentInvestigationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/incidents/investigate")
    public IncidentInvestigationService.Investigation investigate(@RequestBody InvestigateRequest req) {
        return service.investigate(req.alert());
    }
}
