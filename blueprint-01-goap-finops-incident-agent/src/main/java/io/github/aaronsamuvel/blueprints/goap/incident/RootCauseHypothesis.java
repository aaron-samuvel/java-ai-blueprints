package io.github.aaronsamuvel.blueprints.goap.incident;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Most likely root cause of the anomaly")
public record RootCauseHypothesis(
        @JsonPropertyDescription("Cause category, e.g. BAD_DEPLOYMENT, UNDER_PROVISIONED, OVER_PROVISIONED, DEPENDENCY_FAILURE") String category,
        @JsonPropertyDescription("Confidence between 0.0 and 1.0") double confidenceScore,
        @JsonPropertyDescription("Evidence from logs, metrics and runbooks") String evidenceSummary,
        @JsonPropertyDescription("True if the latest deployment should be rolled back") boolean requiresRollback,
        @JsonPropertyDescription("True if capacity should be resized") boolean requiresRightSizing) {}
