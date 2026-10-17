package io.github.aaronsamuvel.blueprints.swarm.commerce.memory;

import java.util.regex.Pattern;

/**
 * The partition key of a conversation: tenant, user and session. It is encoded as the Spring AI
 * conversation id {@code tenant::user::session}, so every {@code ChatMemory} call carries all three.
 */
public record TenantSessionKey(String tenantId, String userId, String sessionId) {

    private static final Pattern PART = Pattern.compile("[A-Za-z0-9._@-]{1,64}");
    private static final String SEP = "::";

    public TenantSessionKey {
        for (String part : new String[] {tenantId, userId, sessionId}) {
            if (part == null || !PART.matcher(part).matches()) {
                throw new IllegalArgumentException("tenantId, userId and sessionId must match " + PART.pattern());
            }
        }
    }

    public String conversationId() {
        return tenantId + SEP + userId + SEP + sessionId;
    }

    public static TenantSessionKey parse(String conversationId) {
        String[] parts = conversationId == null ? new String[0] : conversationId.split(SEP, -1);
        if (parts.length != 3) throw new IllegalArgumentException("conversation id must be tenant::user::session");
        return new TenantSessionKey(parts[0], parts[1], parts[2]);
    }
}
