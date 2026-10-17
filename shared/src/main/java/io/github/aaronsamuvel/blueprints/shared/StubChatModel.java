package io.github.aaronsamuvel.blueprints.shared;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Deterministic {@link ChatModel} for tests and offline demos. It records every prompt it receives,
 * so tests can assert exactly what would have been sent to a real model.
 */
public class StubChatModel implements ChatModel {

    private final Function<Prompt, String> responder;
    private final List<Prompt> received = new CopyOnWriteArrayList<>();

    public StubChatModel(Function<Prompt, String> responder) {
        this.responder = responder;
    }

    /** Echo the text of the last message, prefixed with {@code "echo: "}. */
    public static StubChatModel echo() {
        return new StubChatModel(p -> "echo: " + lastText(p));
    }

    /** Always answer with the same fixed text. */
    public static StubChatModel fixed(String answer) {
        return new StubChatModel(p -> answer);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        received.add(prompt);
        String text = responder.apply(prompt);
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    public List<Prompt> received() {
        return List.copyOf(received);
    }

    public Prompt lastPrompt() {
        return received.isEmpty() ? null : received.get(received.size() - 1);
    }

    /** All text of all messages ever sent to this model, joined with newlines. */
    public String allSentText() {
        StringBuilder sb = new StringBuilder();
        received.forEach(p -> p.getInstructions().forEach(m -> sb.append(m.getText()).append('\n')));
        return sb.toString();
    }

    public static String lastText(Prompt p) {
        var msgs = p.getInstructions();
        return msgs.isEmpty() ? "" : msgs.get(msgs.size() - 1).getText();
    }
}
