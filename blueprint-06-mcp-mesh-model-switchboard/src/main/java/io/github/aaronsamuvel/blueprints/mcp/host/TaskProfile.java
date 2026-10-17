package io.github.aaronsamuvel.blueprints.mcp.host;

import java.time.Duration;

/**
 * What the router looks at. {@code privacySensitive} may be set by the caller; the router also
 * flags prompts that contain obvious personal data itself.
 */
public record TaskProfile(String prompt, boolean privacySensitive, Duration latencyBudget) {

    public TaskProfile {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt is required");
        if (latencyBudget == null || latencyBudget.isNegative() || latencyBudget.isZero()) latencyBudget = Duration.ofSeconds(30);
    }
}
