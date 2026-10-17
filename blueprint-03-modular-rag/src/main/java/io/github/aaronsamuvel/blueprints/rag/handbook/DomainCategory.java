package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The knowledge partitions the branching retriever routes between. Classification is a transparent
 * keyword vote so it is deterministic offline; swap {@link #classify} for an LLM classifier if needed.
 */
public enum DomainCategory {
    ARCHITECTURE(List.of("architecture", "design", "service", "pattern", "adr", "boundary", "event", "topology", "scaling", "cache")),
    SECURITY_POLICY(List.of("security", "policy", "password", "access", "encryption", "secret", "pii", "mfa", "compliance", "retention")),
    RUNBOOKS(List.of("runbook", "incident", "outage", "restart", "rollback", "alert", "on-call", "lag", "recover", "failover"));

    private final List<String> keywords;

    DomainCategory(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> keywords() {
        return keywords;
    }

    private long votes(String lower) {
        return keywords.stream().filter(lower::contains).count();
    }

    /** The category with most keyword hits, or empty when nothing matches (search every partition). */
    public static Optional<DomainCategory> classify(String query) {
        String lower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(c -> c.votes(lower) > 0)
                .max(Comparator.comparingLong((DomainCategory c) -> c.votes(lower)).thenComparing(c -> -c.ordinal()));
    }
}
