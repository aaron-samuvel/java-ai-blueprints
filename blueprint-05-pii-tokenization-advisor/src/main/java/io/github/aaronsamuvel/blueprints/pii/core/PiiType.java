package io.github.aaronsamuvel.blueprints.pii.core;

import java.util.regex.Pattern;

/**
 * PII categories detected by the tokenizer. Patterns are tuned for Indian and common global
 * formats. Order matters: more specific types are matched first.
 */
public enum PiiType {
    EMAIL("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
    PAN("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"),
    CARD("\\b(?:\\d[ -]?){12,18}\\d\\b"),
    AADHAAR("\\b[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}\\b"),
    PHONE("(?<![\\w+])(?:\\+91[ -]?)?[6-9]\\d{4}[ -]?\\d{5}\\b");

    private final Pattern pattern;

    PiiType(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    public Pattern pattern() {
        return pattern;
    }
}
