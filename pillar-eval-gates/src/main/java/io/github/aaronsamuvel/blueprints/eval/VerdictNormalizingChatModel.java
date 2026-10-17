package io.github.aaronsamuvel.blueprints.eval;

import java.util.Locale;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Spring AI's RelevancyEvaluator and FactCheckingEvaluator only pass when the judge replies exactly
 * "yes" (case-insensitive). Small local judges such as llama3.2:3b answer "Yes." or "Yes, the claim ...",
 * which would count as a fail. This decorator reduces the judge's reply to a bare YES/NO verdict.
 */
public class VerdictNormalizingChatModel implements ChatModel {

    private final ChatModel delegate;

    public VerdictNormalizingChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ChatResponse raw = delegate.call(prompt);
        String text = raw.getResult() == null ? "" : raw.getResult().getOutput().getText();
        return new ChatResponse(java.util.List.of(new Generation(new AssistantMessage(normalize(text)))), raw.getMetadata());
    }

    /** ChatClient merges these into every prompt; without delegating, the judge's model name is lost. */
    @Override
    public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    static String normalize(String text) {
        if (text == null) return "NO";
        String t = text.strip().toLowerCase(Locale.ROOT).replaceAll("^[^a-z]+", "");
        if (t.startsWith("yes")) return "YES";
        return "NO";
    }
}
