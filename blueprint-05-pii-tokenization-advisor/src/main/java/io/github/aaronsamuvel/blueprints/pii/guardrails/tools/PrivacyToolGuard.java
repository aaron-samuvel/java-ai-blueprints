package io.github.aaronsamuvel.blueprints.pii.guardrails.tools;

import java.util.ArrayList;
import java.util.List;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiAnalyzerSpi;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacySession;

import org.springframework.ai.tool.ToolCallback;

/**
 * Builds the tool list for one request: tools whose required role the caller lacks are left out
 * of the model's tool schema entirely; the rest are wrapped in {@link GuardedToolCallback}.
 */
public final class PrivacyToolGuard {

    private final ToolPolicyRegistry registry;
    private final PiiAnalyzerSpi analyzer;

    public PrivacyToolGuard(ToolPolicyRegistry registry, PiiAnalyzerSpi analyzer) {
        this.registry = registry;
        this.analyzer = analyzer;
    }

    public List<ToolCallback> authorize(PrivacySession session, List<? extends ToolCallback> tools) {
        List<ToolCallback> visible = new ArrayList<>();
        for (ToolCallback cb : tools) {
            String name = cb.getToolDefinition().name();
            ToolPolicy policy = registry.policyFor(name);
            if (session.callerHas(policy.requiredRole())) {
                visible.add(new GuardedToolCallback(cb, policy, session, analyzer));
                session.audit().offered(name);
            } else {
                session.audit().hidden(name);
            }
        }
        return visible;
    }
}
