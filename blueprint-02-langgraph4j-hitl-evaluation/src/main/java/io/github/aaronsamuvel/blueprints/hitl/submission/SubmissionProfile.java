package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured profile extracted from the raw submission by the parse node. */
@JsonClassDescription("Structured profile of a candidate or vendor submission")
public record SubmissionProfile(
        @JsonPropertyDescription("Name of the candidate or vendor") String name,
        @JsonPropertyDescription("Role or contract the submission is for") String targetRole,
        @JsonPropertyDescription("Total professional experience in whole numbers, 0 if unknown") int experienceYears,
        @JsonPropertyDescription("Technologies and practices named in the document") List<String> keySkills) {
}
