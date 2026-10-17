package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * The {@code llm.provider=mock} model: a deterministic, keyword-based stand-in for the LLM evaluators,
 * so the graph's three exits can be demonstrated and tested offline. It only reads the text between
 * {@code <document>} tags, never the instructions. It is a test double, not a real reviewer.
 */
public final class MockEvaluatorModel {

    static final Map<String, List<String>> KEYWORDS = Map.of(
            SkillsAndArchitectureNode.DIMENSION, List.of("kubernetes", "microservice", "event sourcing", "kafka", "spring boot", "distributed", "api design", "caching"),
            ExperienceAndResilienceNode.DIMENSION, List.of("years", "incident", "on-call", "resilience", "circuit breaker", "chaos", "postmortem", "sre"),
            CultureAndGovernanceNode.DIMENSION, List.of("mentor", "code review", "documentation", "governance", "compliance", "collaborat", "rfc", "inclusive"));

    static final Map<String, String> RED_FLAG_PHRASES = new LinkedHashMap<>();
    static {
        RED_FLAG_PHRASES.put("falsified", "CRITICAL: employment dates are falsified");
        RED_FLAG_PHRASES.put("plagiar", "CRITICAL: design document is plagiarised");
        RED_FLAG_PHRASES.put("hardcoded password", "CRITICAL: credentials hardcoded in the code sample");
        RED_FLAG_PHRASES.put("no automated tests", "code sample has no automated tests");
    }

    private static final Pattern DIMENSION = Pattern.compile("DIMENSION=([a-z-]+)");
    private static final Pattern NAME = Pattern.compile("(?im)^name:\\s*(.+)$");
    private static final Pattern ROLE = Pattern.compile("(?im)^role:\\s*(.+)$");
    private static final Pattern YEARS = Pattern.compile("(\\d+)\\s+years");

    private MockEvaluatorModel() {}

    public static StubChatModel create() {
        return new StubChatModel(MockEvaluatorModel::answer);
    }

    static String answer(Prompt prompt) {
        String system = prompt.getInstructions().stream().filter(m -> m.getMessageType() == MessageType.SYSTEM)
                .map(Message::getText).findFirst().orElse("");
        String doc = document(StubChatModel.lastText(prompt));
        String lower = doc.toLowerCase(Locale.ROOT);
        if (system.contains("TASK: parse-submission")) {
            List<String> skills = KEYWORDS.get(SkillsAndArchitectureNode.DIMENSION).stream().filter(lower::contains).toList();
            Matcher y = YEARS.matcher(lower);
            return "{\"name\":" + quote(first(NAME, doc, "unknown")) + ",\"targetRole\":" + quote(first(ROLE, doc, "unknown"))
                    + ",\"experienceYears\":" + (y.find() ? y.group(1) : "0") + ",\"keySkills\":" + list(skills) + "}";
        }
        if (system.contains("TASK: red-flag-audit")) {
            List<String> flags = new ArrayList<>();
            RED_FLAG_PHRASES.forEach((phrase, flag) -> { if (lower.contains(phrase)) flags.add(flag); });
            return "{\"score\":" + Math.max(0, 100 - 30 * flags.size()) + ",\"redFlags\":" + list(flags) + "}";
        }
        Matcher d = DIMENSION.matcher(system);
        if (d.find() && KEYWORDS.containsKey(d.group(1))) {
            List<String> hits = KEYWORDS.get(d.group(1)).stream().filter(lower::contains).toList();
            int score = Math.min(98, 30 + 14 * hits.size());
            return "{\"dimension\":\"" + d.group(1) + "\",\"score\":" + score + ",\"rationale\":"
                    + quote(hits.isEmpty() ? "no evidence found" : "evidence: " + String.join(", ", hits)) + "}";
        }
        return "{}";
    }

    private static String document(String userText) {
        int start = userText.indexOf("<document>");
        int end = userText.indexOf("</document>");
        return start >= 0 && end > start ? userText.substring(start + 10, end) : "";
    }

    private static String first(Pattern p, String text, String fallback) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : fallback;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String list(List<String> items) {
        return "[" + String.join(",", items.stream().map(MockEvaluatorModel::quote).toList()) + "]";
    }
}
