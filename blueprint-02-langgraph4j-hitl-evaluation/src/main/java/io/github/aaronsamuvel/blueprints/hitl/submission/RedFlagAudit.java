package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Output of the red-flag auditor. A flag that starts with {@code CRITICAL:} forces human escalation. */
@JsonClassDescription("Integrity audit of a submission")
public record RedFlagAudit(
        @JsonPropertyDescription("Integrity score from 0 to 100; 100 means no concerns") int score,
        @JsonPropertyDescription("Concerns found; prefix a concern with CRITICAL: when it alone should block automation") List<String> redFlags) {
}
