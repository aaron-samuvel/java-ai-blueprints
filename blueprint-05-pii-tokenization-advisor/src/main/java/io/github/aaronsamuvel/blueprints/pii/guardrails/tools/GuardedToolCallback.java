package io.github.aaronsamuvel.blueprints.pii.guardrails.tools;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiAnalyzerSpi;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyAudit;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacySession;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyTokenVault;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Wraps one tool (local {@code @Tool} or MCP) at the privacy boundary: re-checks the role,
 * restores only the allowed placeholder types in the arguments, and tokenises the result before
 * it goes back to the model. Every call is written to the session's audit trail.
 */
public final class GuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolPolicy policy;
    private final PrivacySession session;
    private final PiiAnalyzerSpi analyzer;

    public GuardedToolCallback(ToolCallback delegate, ToolPolicy policy, PrivacySession session, PiiAnalyzerSpi analyzer) {
        this.delegate = delegate;
        this.policy = policy;
        this.session = session;
        this.analyzer = analyzer;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = getToolDefinition().name();
        if (!session.callerHas(policy.requiredRole())) {
            String denied = "{\"error\":\"caller is not authorized to use " + name + "\"}";
            session.audit().disclosure(new PrivacyAudit.ToolDisclosure(name, toolInput, null, java.util.List.of(), java.util.List.of(), denied, true));
            return denied;
        }
        PrivacyTokenVault.Restoration args = session.vault().restore(toolInput, policy.allowedPiiTypes());
        String raw = toolContext == null ? delegate.call(args.text()) : delegate.call(args.text(), toolContext);
        String safe = session.vault().tokenize(raw, analyzer);
        session.audit().disclosure(new PrivacyAudit.ToolDisclosure(name, toolInput, args.text(),
                args.restored().stream().map(PrivacyTokenVault.Entry::type).distinct().toList(), args.withheld(), safe, false));
        return safe;
    }
}
