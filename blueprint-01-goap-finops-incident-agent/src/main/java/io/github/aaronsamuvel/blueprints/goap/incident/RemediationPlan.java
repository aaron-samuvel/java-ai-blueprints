package io.github.aaronsamuvel.blueprints.goap.incident;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.aaronsamuvel.blueprints.goap.platform.HasContent;

@JsonClassDescription("Verified remediation and FinOps plan for an incident")
public record RemediationPlan(
        @JsonPropertyDescription("Incident identifier") String incidentId,
        @JsonPropertyDescription("Two-sentence summary for executives") String executiveSummary,
        @JsonPropertyDescription("kubectl or terraform commands to apply, in order") List<String> kubectlOrTerraformCommands,
        @JsonPropertyDescription("Unified diff of the pull request that makes the fix permanent") String pullRequestDiff,
        @JsonPropertyDescription("Estimated monthly savings in USD (0 if none)") double estimatedMonthlySavingsUsd) implements HasContent {

    public RemediationPlan {
        kubectlOrTerraformCommands = kubectlOrTerraformCommands == null ? List.of() : List.copyOf(kubectlOrTerraformCommands);
    }

    @Override
    public String content() {
        StringBuilder sb = new StringBuilder("## Remediation plan ").append(incidentId).append("\n\n").append(executiveSummary).append("\n\n");
        kubectlOrTerraformCommands.forEach(c -> sb.append("- `").append(c).append("`\n"));
        sb.append("\nEstimated monthly savings: USD ").append(estimatedMonthlySavingsUsd).append('\n');
        if (pullRequestDiff != null && !pullRequestDiff.isBlank()) sb.append("\n```diff\n").append(pullRequestDiff).append("\n```\n");
        return sb.toString();
    }
}
