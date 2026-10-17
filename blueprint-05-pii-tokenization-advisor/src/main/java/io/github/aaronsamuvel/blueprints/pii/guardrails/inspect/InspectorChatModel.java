package io.github.aaronsamuvel.blueprints.pii.guardrails.inspect;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;

import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyTokenVault;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Deterministic stand-in for the cloud LLM, used by the inspector. It records exactly what it
 * receives (the "LLM boundary"), and behaves like a tool-using assistant:
 * <ul>
 *   <li>"balance" + an account placeholder calls {@code accountBalance};</li>
 *   <li>"contact"/"email" + a person placeholder calls {@code customerContact};</li>
 *   <li>"branch"/"hours" + a location placeholder calls {@code branchHours};</li>
 *   <li>after a tool result it answers with that result;</li>
 *   <li>otherwise it answers by listing the protected values it was given;</li>
 *   <li>"simulate-leak" makes it answer with a raw (made-up) email, to exercise BLOCK_ON_LEAK.</li>
 * </ul>
 * Only the tools actually offered to it can be called.
 */
public final class InspectorChatModel implements ChatModel {

    private final List<List<String>> received = new CopyOnWriteArrayList<>();
    private final List<String> answers = new CopyOnWriteArrayList<>();
    private int callIds;

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        List<String> seen = new ArrayList<>();
        for (Message m : prompt.getInstructions()) seen.add(render(m));
        received.add(List.copyOf(seen));

        List<Message> msgs = prompt.getInstructions();
        Message last = msgs.get(msgs.size() - 1);
        AssistantMessage out;
        if (last instanceof ToolResponseMessage t) {
            out = new AssistantMessage("Result: " + String.join("; ", t.getResponses().stream().map(ToolResponseMessage.ToolResponse::responseData).toList()));
        } else {
            String user = msgs.stream().filter(UserMessage.class::isInstance).reduce((a, b) -> b).map(Message::getText).orElse("");
            Set<String> tools = offeredTools(prompt);
            out = decide(user.toLowerCase(java.util.Locale.ROOT), user, tools);
        }
        answers.add(render(out));
        return new ChatResponse(List.of(new Generation(out)));
    }

    private AssistantMessage decide(String lower, String user, Set<String> tools) {
        if (lower.contains("simulate-leak")) {
            return new AssistantMessage("Please escalate to ravi.kumar@example.net");
        }
        if (tools.contains("accountBalance") && lower.contains("balance")) {
            String acct = first(user, "ACCOUNT");
            if (acct != null) return toolCall("accountBalance", Map.of("accountNumber", acct));
        }
        if (tools.contains("customerContact") && (lower.contains("contact") || lower.contains("email"))) {
            String person = first(user, "PERSON");
            if (person != null) return toolCall("customerContact", Map.of("customerName", person));
        }
        if (tools.contains("branchHours") && (lower.contains("branch") || lower.contains("hours"))) {
            String city = first(user, "LOCATION");
            if (city != null) return toolCall("branchHours", Map.of("city", city));
        }
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher m = PrivacyTokenVault.PLACEHOLDER.matcher(user);
        while (m.find()) placeholders.add(m.group());
        return new AssistantMessage(placeholders.isEmpty()
                ? "I could not find anything to act on."
                : "I can act on " + String.join(", ", placeholders) + " but no permitted tool applies.");
    }

    private AssistantMessage toolCall(String name, Map<String, String> args) {
        String json = args.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                .reduce((a, b) -> a + "," + b).map(s -> "{" + s + "}").orElse("{}");
        return AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + (++callIds), "function", name, json))).build();
    }

    private static String first(String text, String label) {
        Matcher m = PrivacyTokenVault.PLACEHOLDER.matcher(text);
        while (m.find()) if (m.group(1).equals(label)) return m.group();
        return null;
    }

    private static Set<String> offeredTools(Prompt prompt) {
        Set<String> names = new LinkedHashSet<>();
        if (prompt.getOptions() instanceof ToolCallingChatOptions o) {
            for (ToolCallback cb : o.getToolCallbacks()) names.add(cb.getToolDefinition().name());
        }
        return names;
    }

    static String render(Message m) {
        return switch (m) {
            case AssistantMessage a when a.hasToolCalls() -> "ASSISTANT tool calls: " + a.getToolCalls().stream()
                    .map(tc -> tc.name() + tc.arguments()).toList();
            case ToolResponseMessage t -> "TOOL: " + t.getResponses().stream().map(r -> r.name() + " -> " + r.responseData()).toList();
            default -> m.getMessageType() + ": " + m.getText();
        };
    }

    /** Every prompt the model received, one list of rendered messages per call. */
    public List<List<String>> received() {
        return List.copyOf(received);
    }

    /** Every answer the model produced, before the response policy ran. */
    public List<String> answers() {
        return List.copyOf(answers);
    }
}
