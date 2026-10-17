package io.github.aaronsamuvel.blueprints.pii.guardrails.vault;

import org.springframework.security.core.Authentication;

/**
 * Everything one request needs at the privacy boundary: its vault, its audit trail, the caller's
 * Spring Security {@link Authentication} and the response policy. Create one per request and
 * pass it to the advisor (context key {@link #CONTEXT_KEY}) and to the tool guard.
 */
public record PrivacySession(PrivacyTokenVault vault, PrivacyAudit audit, Authentication caller, ResponsePolicy policy) {

    public static final String CONTEXT_KEY = "privacy.session";

    public static PrivacySession start(Authentication caller, ResponsePolicy policy) {
        return new PrivacySession(new PrivacyTokenVault(), new PrivacyAudit(), caller, policy);
    }

    public boolean callerHas(String authority) {
        return authority == null || authority.isBlank()
                || (caller != null && caller.isAuthenticated()
                    && caller.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority())));
    }
}
