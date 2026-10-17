package io.github.aaronsamuvel.blueprints.goap.web;

import io.github.aaronsamuvel.blueprints.goap.ooda.BillingCsv;
import io.github.aaronsamuvel.blueprints.goap.ooda.FinOpsIncidentAgent;
import io.github.aaronsamuvel.blueprints.goap.ooda.IncidentReport;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentController {

    private final FinOpsIncidentAgent agent;

    public IncidentController(FinOpsIncidentAgent agent) {
        this.agent = agent;
    }

    /** Body: billing CSV ({@code day,service,cost_inr,cpu}). */
    @PostMapping(value = "/api/incidents/run", consumes = "text/csv")
    public IncidentReport run(@RequestBody String csv) {
        return agent.run(BillingCsv.parse(csv));
    }
}
