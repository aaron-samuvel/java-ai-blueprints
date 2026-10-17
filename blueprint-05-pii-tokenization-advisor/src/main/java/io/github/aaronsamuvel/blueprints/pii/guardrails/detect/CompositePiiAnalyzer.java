package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyTokenVault;

/**
 * Runs every analyzer and resolves overlaps: the longer span wins, then the higher score. Spans
 * inside existing placeholders ({@code <PII:...>}) are ignored, so already-tokenised text is stable.
 */
public final class CompositePiiAnalyzer implements PiiAnalyzerSpi {

    private final List<PiiAnalyzerSpi> analyzers;

    public CompositePiiAnalyzer(List<PiiAnalyzerSpi> analyzers) {
        this.analyzers = List.copyOf(analyzers);
    }

    public List<String> analyzerNames() {
        return analyzers.stream().map(PiiAnalyzerSpi::name).toList();
    }

    @Override
    public String name() {
        return "composite" + analyzerNames();
    }

    @Override
    public List<PiiSpan> analyze(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<int[]> placeholders = new ArrayList<>();
        Matcher m = PrivacyTokenVault.PLACEHOLDER.matcher(text);
        while (m.find()) placeholders.add(new int[] {m.start(), m.end()});

        List<PiiSpan> all = new ArrayList<>();
        for (PiiAnalyzerSpi a : analyzers) {
            for (PiiSpan s : a.analyze(text)) {
                boolean insidePlaceholder = placeholders.stream().anyMatch(p -> s.start() < p[1] && p[0] < s.end());
                if (!insidePlaceholder) all.add(s);
            }
        }
        all.sort(Comparator.comparingInt(PiiSpan::length).reversed().thenComparing(Comparator.comparingDouble(PiiSpan::score).reversed()));
        List<PiiSpan> kept = new ArrayList<>();
        for (PiiSpan s : all) {
            if (kept.stream().noneMatch(s::overlaps)) kept.add(s);
        }
        kept.sort(Comparator.comparingInt(PiiSpan::start));
        return kept;
    }
}
