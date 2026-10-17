package io.github.aaronsamuvel.blueprints.lifecycle.examples;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * J-AIDLC stage 1 (domain/intent modelling with records): the record IS the contract the model
 * must fill. The Jackson descriptions go into the JSON schema that {@code BeanOutputConverter}
 * sends the model, and the compact constructor rejects malformed model output early.
 */
@JsonClassDescription("Triage of a production incident")
public record IncidentTriage(
        @JsonPropertyDescription("Impact level: LOW, MEDIUM, HIGH or CRITICAL") Severity severity,
        @JsonPropertyDescription("Name of the affected service") String service,
        @JsonPropertyDescription("One-sentence summary of the symptom") String summary,
        @JsonPropertyDescription("Ordered next actions for the on-call engineer") List<String> nextSteps) {

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public IncidentTriage {
        if (severity == null) throw new IllegalArgumentException("severity is required");
        if (service == null || service.isBlank()) throw new IllegalArgumentException("service is required");
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
    }
}
