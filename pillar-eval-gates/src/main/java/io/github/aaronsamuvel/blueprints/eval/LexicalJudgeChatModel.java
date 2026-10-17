package io.github.aaronsamuvel.blueprints.eval;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Deterministic stand-in for an LLM judge, for offline evaluation gates. It reads the prompts that
 * Spring AI's {@code RelevancyEvaluator} ("Response: ... Context: ...") and
 * {@code FactCheckingEvaluator} ("Document: ... Claim: ...") send, and answers YES or NO depending on
 * how many content words of the response/claim appear in the context/document. Numbers count as
 * content words, so changed figures (30 days to 90 days) are caught.
 * <p>
 * This is a lexical heuristic, not semantic understanding. It's good enough to catch hallucinated
 * facts and off-topic answers in tests. Use a real model judge under {@code -Pit} for nuance.
 */
public class LexicalJudgeChatModel implements ChatModel {

    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "are", "with", "that", "this", "from", "your", "you", "can", "will", "have",
            "has", "was", "were", "been", "they", "their", "there", "what", "when", "which", "into", "about",
            "within", "also", "only", "some", "than", "then", "them", "its", "our", "may", "per", "not", "any");

    private final double threshold;
    private final List<Double> scores = new CopyOnWriteArrayList<>();

    public LexicalJudgeChatModel() {
        this(0.7);
    }

    public LexicalJudgeChatModel(double threshold) {
        this.threshold = threshold;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        String text = prompt.getContents();
        String candidate;
        String evidence;
        if (text.contains("Claim:")) {
            evidence = between(text, "Document:", "Claim:");
            candidate = after(text, "Claim:");
        } else {
            candidate = between(text, "Response:", "Context:");
            evidence = between(text, "Context:", "Answer:");
        }
        double support = support(candidate, evidence);
        scores.add(support);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(support >= threshold ? "YES" : "NO"))));
    }

    /** Fraction of the candidate's content words that occur in the evidence (1.0 if it has none). */
    public static double support(String candidate, String evidence) {
        Set<String> c = words(candidate);
        if (c.isEmpty()) return 1.0;
        Set<String> e = words(evidence);
        long hit = c.stream().filter(e::contains).count();
        return (double) hit / c.size();
    }

    static Set<String> words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(w -> !w.isEmpty() && !STOP.contains(w) && (w.length() > 2 || w.chars().allMatch(Character::isDigit)))
                .map(LexicalJudgeChatModel::stem)
                .collect(Collectors.toSet());
    }

    private static String stem(String w) {
        if (w.length() > 4 && w.endsWith("s")) return w.substring(0, w.length() - 1);
        return w;
    }

    private static String between(String s, String a, String b) {
        int i = s.indexOf(a);
        if (i < 0) return "";
        int j = s.indexOf(b, i + a.length());
        return j < 0 ? s.substring(i + a.length()) : s.substring(i + a.length(), j);
    }

    private static String after(String s, String a) {
        int i = s.indexOf(a);
        return i < 0 ? "" : s.substring(i + a.length());
    }

    public List<Double> scores() {
        return List.copyOf(scores);
    }
}
