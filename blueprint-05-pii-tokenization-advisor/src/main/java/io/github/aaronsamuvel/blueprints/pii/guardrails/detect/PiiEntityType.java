package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

/**
 * Entity types the guardrails detect. {@link #label()} is the part used in placeholders such as
 * {@code <PII:ACCOUNT_1>}.
 */
public enum PiiEntityType {
    EMAIL("EMAIL"),
    PHONE("PHONE"),
    CREDIT_CARD("CARD"),
    SSN("SSN"),
    PAN("PAN"),
    AADHAAR("AADHAAR"),
    IBAN("IBAN"),
    ACCOUNT_NUMBER("ACCOUNT"),
    PERSON("PERSON"),
    LOCATION("LOCATION");

    private final String label;

    PiiEntityType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
