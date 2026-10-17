package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Keeps the transcript under a token budget: when the estimate exceeds {@code maxTokens}, every
 * entry except the last {@code keepRecent} is folded into one summary entry. Tokens are estimated
 * as characters / 4. The summarizer is pluggable (a model call in production); the default keeps
 * the first line of each folded entry, truncated, so tables of intermediate rows disappear.
 */
public final class ContextCompactor {

    public static final String SUMMARY_PREFIX = "[compacted] ";

    private final int maxTokens;
    private final int keepRecent;
    private final Function<List<String>, String> summarizer;

    public ContextCompactor(int maxTokens, int keepRecent, Function<List<String>, String> summarizer) {
        this.maxTokens = maxTokens;
        this.keepRecent = keepRecent;
        this.summarizer = summarizer;
    }

    public ContextCompactor(int maxTokens, int keepRecent) {
        this(maxTokens, keepRecent, ContextCompactor::firstLines);
    }

    public static int estimateTokens(List<String> entries) {
        return entries.stream().mapToInt(String::length).sum() / 4;
    }

    public boolean compactIfNeeded(WorkflowContext ctx) {
        List<String> t = ctx.transcript();
        if (estimateTokens(t) <= maxTokens || t.size() <= keepRecent) return false;
        List<String> old = t.subList(0, t.size() - keepRecent);
        List<String> out = new ArrayList<>();
        out.add(SUMMARY_PREFIX + summarizer.apply(old));
        out.addAll(t.subList(t.size() - keepRecent, t.size()));
        ctx.replaceTranscript(out);
        return true;
    }

    static String firstLines(List<String> entries) {
        return entries.size() + " earlier turns: " + String.join(" | ", entries.stream()
                .map(e -> e.lines().findFirst().orElse(""))
                .map(l -> l.length() > 80 ? l.substring(0, 80) + "..." : l)
                .toList());
    }
}
