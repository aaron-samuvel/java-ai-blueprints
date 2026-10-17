package io.github.aaronsamuvel.blueprints.lifecycle.examples;

import java.util.List;

/**
 * J-AIDLC stage 1 (domain/intent modelling with records): the record IS the contract the model
 * must fill. Compact-constructor validation rejects malformed model output early.
 */
public record IncidentTriage(Severity severity, String service, String summary, List<String> nextSteps) {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public IncidentTriage {
        if (severity == null) throw new IllegalArgumentException("severity is required");
        if (service == null || service.isBlank()) throw new IllegalArgumentException("service is required");
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
    }
}
