package io.github.aaronsamuvel.blueprints.pii.guardrails.vault;

/** What happens to placeholders in the model's final answer before it reaches the caller. */
public enum ResponsePolicy {
    /** Restore raw values if the caller holds the detokenize authority, otherwise redact. */
    DETOKENIZE_FOR_AUTHORIZED_USER,
    /** Replace placeholders with {@code [REDACTED_TYPE]} markers. */
    REDACT,
    /** Keep placeholders; if the answer contains raw PII anyway, block the whole answer. */
    BLOCK_ON_LEAK
}
