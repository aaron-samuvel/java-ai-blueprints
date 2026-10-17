package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

import java.util.List;

/**
 * Pluggable PII detector (handbook Part III, Blueprint 5: "PiiAnalyzerSpi"). Implementations:
 * {@link RegexPiiAnalyzer}, {@link OpenNlpPiiAnalyzer}, {@link PresidioPiiAnalyzer}; they are
 * combined by {@link CompositePiiAnalyzer}.
 */
public interface PiiAnalyzerSpi {

    String name();

    /** Spans found in {@code text}; may overlap with spans of other analyzers. */
    List<PiiSpan> analyze(String text);
}
