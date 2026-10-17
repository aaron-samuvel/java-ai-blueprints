package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One evaluator's verdict on one dimension of a submission. Serializable so checkpoints can persist it. */
@JsonClassDescription("A score from 0 to 100 for one review dimension, with a short rationale")
public record DimensionScore(
        @JsonPropertyDescription("Dimension id, for example skills-architecture") String dimension,
        @JsonPropertyDescription("Score from 0 (absent) to 100 (outstanding)") int score,
        @JsonPropertyDescription("One or two sentences citing evidence from the document") String rationale)
        implements Serializable {
}
