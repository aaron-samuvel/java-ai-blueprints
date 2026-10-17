package io.github.aaronsamuvel.blueprints.pii.guardrails.vault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiAnalyzerSpi;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiSpan;

/**
 * Request-scoped vault mapping raw PII values to deterministic placeholders such as
 * {@code <PII:EMAIL_1>} and {@code <PII:ACCOUNT_1>}. The same value always gets the same placeholder
 * within one vault; numbering restarts per vault (per request). It never leaves the JVM.
 */
public final class PrivacyTokenVault {

    public static final Pattern PLACEHOLDER = Pattern.compile("<PII:([A-Z]+)_(\\d+)>");

    public record Entry(String placeholder, PiiEntityType type, String value) {}

    /** Result of a selective restore: text, plus which placeholders were restored or withheld. */
    public record Restoration(String text, List<Entry> restored, List<String> withheld) {}

    private final Map<String, Entry> byValue = new LinkedHashMap<>();
    private final Map<String, Entry> byPlaceholder = new LinkedHashMap<>();
    private final Map<PiiEntityType, Integer> counters = new EnumMap<>(PiiEntityType.class);

    /**
     * Replaces every span found by {@code analyzer} with its placeholder, then replaces any value
     * already in this vault that the detectors missed in this text (for example an account number
     * a tool echoes back without the "account" keyword).
     */
    public synchronized String tokenize(String text, PiiAnalyzerSpi analyzer) {
        if (text == null || text.isEmpty()) return text;
        List<PiiSpan> spans = analyzer.analyze(text);
        StringBuilder sb = new StringBuilder();
        int at = 0;
        for (PiiSpan s : spans) {
            if (s.start() < at) continue;
            sb.append(text, at, s.start()).append(placeholderFor(s.type(), s.text()));
            at = s.end();
        }
        String out = sb.append(text.substring(at)).toString();
        List<Entry> known = new ArrayList<>(byValue.values());
        known.sort((a, b) -> b.value().length() - a.value().length());
        for (Entry e : known) {
            if (out.contains(e.value())) out = out.replace(e.value(), e.placeholder());
        }
        return out;
    }

    public synchronized String placeholderFor(PiiEntityType type, String value) {
        Entry e = byValue.get(value);
        if (e == null) {
            int n = counters.merge(type, 1, Integer::sum);
            e = new Entry("<PII:" + type.label() + "_" + n + ">", type, value);
            byValue.put(value, e);
            byPlaceholder.put(e.placeholder(), e);
        }
        return e.placeholder();
    }

    /** Restores only placeholders whose type is in {@code allowed}; the others stay as placeholders. */
    public synchronized Restoration restore(String text, Collection<PiiEntityType> allowed) {
        if (text == null || text.isEmpty()) return new Restoration(text, List.of(), List.of());
        Set<PiiEntityType> ok = allowed.isEmpty() ? Set.of() : Set.copyOf(allowed);
        List<Entry> restored = new ArrayList<>();
        Set<String> withheld = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Entry e = byPlaceholder.get(m.group());
            String replacement = m.group();
            if (e != null && ok.contains(e.type())) {
                replacement = e.value();
                restored.add(e);
            } else if (e != null) {
                withheld.add(e.placeholder());
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return new Restoration(sb.toString(), restored, List.copyOf(withheld));
    }

    public Restoration restoreAll(String text) {
        return restore(text, Set.of(PiiEntityType.values()));
    }

    /** Replaces placeholders with irreversible type markers such as {@code [REDACTED_EMAIL]}. */
    public synchronized String redactPlaceholders(String text) {
        if (text == null) return null;
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, "[REDACTED_" + m.group(1) + "]");
        m.appendTail(sb);
        return sb.toString();
    }

    /** Raw values present in {@code text} (a leak check); returns entries, never logs values. */
    public synchronized List<Entry> rawValuesIn(String text) {
        if (text == null || text.isEmpty()) return List.of();
        return byValue.values().stream().filter(e -> text.contains(e.value())).toList();
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(byPlaceholder.values());
    }

    public synchronized int size() {
        return byPlaceholder.size();
    }
}
