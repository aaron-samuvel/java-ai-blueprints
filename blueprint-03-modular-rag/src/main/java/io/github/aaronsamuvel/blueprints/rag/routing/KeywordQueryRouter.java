package io.github.aaronsamuvel.blueprints.rag.routing;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic, zero-cost router based on keywords. Good enough as a first stage and fully
 * testable; an LLM router can be layered on top for ambiguous queries.
 */
public class KeywordQueryRouter implements QueryRouter {

    private static final Pattern SMALLTALK = Pattern.compile(
            "^(hi|hello|hey|thanks|thank you|good (morning|evening|afternoon))\\b.*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> POLICY = Set.of(
            "refund", "return", "leave", "holiday", "policy", "damaged", "replacement", "sick", "salary");
    private static final Set<String> TECHNICAL = Set.of(
            "kafka", "broker", "consumer", "lag", "spring", "port", "startup", "error", "disk", "exception", "deploy");

    @Override
    public QueryIntent route(String query) {
        if (query == null || query.isBlank()) return QueryIntent.SMALLTALK;
        String q = query.strip();
        if (SMALLTALK.matcher(q).matches() && q.split("\\s+").length <= 4) return QueryIntent.SMALLTALK;
        int p = 0, t = 0;
        for (String w : q.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (POLICY.contains(w)) p++;
            if (TECHNICAL.contains(w)) t++;
        }
        if (p > t) return QueryIntent.POLICY;
        if (t > p) return QueryIntent.TECHNICAL;
        return QueryIntent.GENERAL;
    }
}
