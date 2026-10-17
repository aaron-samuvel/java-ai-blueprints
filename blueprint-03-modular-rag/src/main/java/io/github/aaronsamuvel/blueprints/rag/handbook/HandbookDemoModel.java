package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.ai.chat.prompt.Prompt;

/**
 * Offline stand-in for the LLM in the handbook RAG pipeline (used when {@code blueprints.model=stub}).
 * Each step is answered deterministically: query compression and rewriting return the cleaned query,
 * expansion returns three variants, the enrichers get an extractive summary and the most frequent
 * words, and the final answer quotes the context sentences that best overlap with the question.
 * It is extractive by construction, so it can't hallucinate. It is not a language model.
 */
public final class HandbookDemoModel {

    static final String ABSTAIN = "I don't know. The knowledge base has no document that answers this question closely enough.";
    private static final Set<String> FILLER = Set.of("please", "can", "you", "tell", "me", "what", "is", "the", "a", "an", "do", "we",
            "our", "how", "should", "i", "about", "for", "of", "to", "in", "on", "and", "are", "does");

    private HandbookDemoModel() {}

    public static StubChatModel create() {
        return new StubChatModel(HandbookDemoModel::answer);
    }

    static String answer(Prompt prompt) {
        String text = StubChatModel.lastText(prompt);
        if (text.contains("TASK: compress-query")) return after(text, "Follow-up query:");
        if (text.contains("TASK: rewrite-query")) return rewrite(after(text, "Original query:"));
        if (text.contains("TASK: expand-query")) {
            String q = rewrite(after(text, "Original query:"));
            return q + "\n" + q + " guidance\n" + "steps for " + q;
        }
        if (text.contains("unique keywords")) return keywords(text);
        if (text.contains("Summarize the key topics")) return firstSentence(text.substring(0, text.indexOf("Summarize the key topics")));
        if (text.contains("---------------------")) {
            String[] parts = text.split("-{21}");
            String context = parts.length > 1 ? parts[1] : "";
            return extractiveAnswer(context, after(text, "Query:"));
        }
        if (text.toLowerCase(Locale.ROOT).contains("outside your knowledge base")) return ABSTAIN;
        return "Hello! Ask me about architecture, security policies or runbooks.";
    }

    private static String after(String text, String marker) {
        int i = text.lastIndexOf(marker);
        if (i < 0) return text.strip();
        String rest = text.substring(i + marker.length()).strip();
        int nl = rest.indexOf('\n');
        return (nl < 0 ? rest : rest.substring(0, nl)).strip();
    }

    static String rewrite(String query) {
        String cleaned = Arrays.stream(query.toLowerCase(Locale.ROOT).replaceAll("[?!.]", " ").split("\\s+"))
                .filter(w -> !w.isBlank() && !FILLER.contains(w))
                .collect(Collectors.joining(" "));
        return cleaned.isBlank() ? query.strip() : cleaned;
    }

    private static List<String> words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}-]+")).filter(w -> w.length() > 3 && !FILLER.contains(w)).toList();
    }

    private static String keywords(String text) {
        Map<String, Long> counts = words(text.substring(0, text.indexOf("unique keywords"))).stream()
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(5)
                .map(Map.Entry::getKey).collect(Collectors.joining(", "));
    }

    private static String firstSentence(String text) {
        for (String s : sentences(text)) if (s.length() > 20) return s;
        return text.strip();
    }

    private static List<String> sentences(String text) {
        return Arrays.stream(text.split("(?<=[.!?])\\s+|\\n+")).map(String::strip).filter(s -> !s.isBlank()).toList();
    }

    private static String extractiveAnswer(String context, String query) {
        Set<String> q = Set.copyOf(words(query));
        List<String> best = sentences(context).stream()
                .filter(s -> s.length() > 20 && !s.contains(":") || s.length() > 60)
                .sorted(Comparator.comparingLong((String s) -> words(s).stream().filter(q::contains).count()).reversed())
                .limit(2)
                .filter(s -> words(s).stream().anyMatch(q::contains))
                .toList();
        if (best.isEmpty()) return ABSTAIN;
        return best.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
    }
}
