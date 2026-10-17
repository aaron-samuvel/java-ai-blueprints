package io.github.aaronsamuvel.blueprints.rag.routing;

/** Branches of the RAG pipeline. POLICY and TECHNICAL map to corpus domains. */
public enum QueryIntent {
    /** Greetings and thanks: answered without retrieval. */
    SMALLTALK(null),
    POLICY("policy"),
    TECHNICAL("technical"),
    /** Not clearly either: search all domains. */
    GENERAL(null);

    private final String domain;

    QueryIntent(String domain) {
        this.domain = domain;
    }

    /** Metadata value of {@code domain} to filter on, or null for no filter. */
    public String domain() {
        return domain;
    }

    public boolean needsRetrieval() {
        return this != SMALLTALK;
    }
}
