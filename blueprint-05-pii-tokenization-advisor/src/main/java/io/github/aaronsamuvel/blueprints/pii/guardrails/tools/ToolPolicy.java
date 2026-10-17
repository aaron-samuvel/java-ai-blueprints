package io.github.aaronsamuvel.blueprints.pii.guardrails.tools;

import java.util.Set;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;

/** Resolved policy of one tool, from {@link ToolPrivacyPolicy} or registered for an MCP tool. */
public record ToolPolicy(Set<PiiEntityType> allowedPiiTypes, String requiredRole) {

    /** Least privilege: nothing restored, no role required. */
    public static final ToolPolicy NONE = new ToolPolicy(Set.of(), "");

    public ToolPolicy {
        allowedPiiTypes = Set.copyOf(allowedPiiTypes);
        requiredRole = requiredRole == null ? "" : requiredRole;
    }
}
