package io.github.aaronsamuvel.blueprints.swarm.commerce;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * Offline model for the commerce swarm ({@code blueprints.model=stub}). It answers each agent's
 * {@code TASK:} prompt deterministically and, for the order agent, emits real tool calls that Spring
 * AI's tool-calling advisor executes. Its first refund proposal deliberately adds 18% "GST" on top of
 * the price paid, so the evaluator-optimizer loop has something to correct. It is a test double.
 */
public class DemoCommerceModel implements ChatModel {

    private static final Pattern SKU = Pattern.compile("SKU-[A-Z0-9]+-\\d+", Pattern.CASE_INSENSITIVE);
    private static final Pattern QTY = Pattern.compile("\\b(\\d{1,2})\\b(?!\\d)");
    private static final Pattern ORDER_ID = Pattern.compile("order\\s*#?\\s*(\\d{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_ID = Pattern.compile("item\\s*#?\\s*(\\d{4,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT_PRICE = Pattern.compile("at INR ([\\d.]+) each");
    private static final Pattern REQUESTED = Pattern.compile("Requested quantity: (\\d+)");
    private static final Pattern PRODUCT_LINE = Pattern.compile("(?m)^(SKU-\\S+) \\| (.+?) \\| INR (\\S+) \\| stock (\\d+)$");

    private final List<Prompt> received = new CopyOnWriteArrayList<>();
    private final AtomicInteger toolCallIds = new AtomicInteger();

    public List<Prompt> received() {
        return List.copyOf(received);
    }

    /** Tool-calling options, so ChatClient attaches the agent's tools and the tool-calling advisor runs them. */
    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        received.add(prompt);
        return new ChatResponse(List.of(new Generation(answer(prompt))));
    }

    private AssistantMessage answer(Prompt prompt) {
        List<Message> msgs = prompt.getInstructions();
        String system = msgs.stream().filter(m -> m.getMessageType() == MessageType.SYSTEM).map(Message::getText).findFirst().orElse("");
        Message last = msgs.getLast();
        String user = withoutFormatInstructions(msgs.stream().filter(m -> m.getMessageType() == MessageType.USER)
                .reduce((a, b) -> b).map(Message::getText).orElse(""));
        String userLower = user.toLowerCase(Locale.ROOT);
        if (system.contains("TASK: triage")) return text(triage(userLower));
        if (system.contains("TASK: recommend")) return text(recommend(system));
        if (system.contains("TASK: order")) {
            if (last instanceof ToolResponseMessage tool) {
                ToolResponseMessage.ToolResponse r = tool.getResponses().getFirst();
                return text("Done (" + r.name() + "): " + r.responseData());
            }
            Matcher order = ORDER_ID.matcher(user);
            if (userLower.contains("status") && order.find()) {
                return toolCall("orderStatus", "{\"orderId\":" + order.group(1) + "}");
            }
            Matcher sku = SKU.matcher(user);
            if (!sku.find()) return text("Which product SKU would you like to order?");
            Matcher qty = QTY.matcher(user.replace(sku.group(), ""));
            int quantity = qty.find() ? Integer.parseInt(qty.group(1)) : 1;
            return toolCall("placeOrder", "{\"sku\":\"" + sku.group().toUpperCase(Locale.ROOT) + "\",\"quantity\":" + quantity + "}");
        }
        if (system.contains("TASK: refund-extract")) {
            Matcher item = ITEM_ID.matcher(user);
            Matcher qty = Pattern.compile("(\\d{1,2})\\s*(?:units?|pieces?|of them|x\\b)").matcher(userLower);
            return text("{\"orderItemId\":" + (item.find() ? item.group(1) : "null") + ",\"quantity\":" + (qty.find() ? qty.group(1) : "1")
                    + ",\"reason\":\"" + user.replace("\"", "'") + "\"}");
        }
        if (system.contains("TASK: refund-propose")) return text(propose(system));
        return text("I can help you find products, place an order or check its status, and handle refunds or disputes.");
    }

    /** Structured-output calls append JSON format instructions to the user text; the demo ignores them. */
    private static String withoutFormatInstructions(String text) {
        int i = text.indexOf("Your response should be in JSON format");
        return (i < 0 ? text : text.substring(0, i)).strip();
    }

    private static boolean has(String text, String regex) {
        return Pattern.compile("\\b(" + regex + ")\\b").matcher(text).find();
    }

    private static String triage(String m) {
        if (has(m, "refund|return|damaged|broken|dispute|money back")) return route("REFUND_DISPUTE", "refund or dispute request");
        if (has(m, "order|buy|purchase|status") || SKU.matcher(m).find()) return route("ORDER_EXECUTION", "order placement or status");
        if (has(m, "find|recommend|looking for|search|show|suggest|need")) return route("PRODUCT_DISCOVERY", "product search");
        return route("GENERAL", "no specialist needed");
    }

    private static String route(String route, String reason) {
        return "{\"route\":\"" + route + "\",\"reason\":\"" + reason + "\"}";
    }

    private static String recommend(String system) {
        Matcher m = PRODUCT_LINE.matcher(system);
        StringBuilder sb = new StringBuilder("Here is what I found:");
        int n = 0;
        while (m.find() && n++ < 2) sb.append("\n- ").append(m.group(2)).append(" (").append(m.group(1)).append("), INR ").append(m.group(3));
        return n == 0 ? "I couldn't find a matching product." : sb.toString();
    }

    private static String propose(String system) {
        boolean feedback = system.contains("Policy feedback");
        if (feedback && system.contains("must be DENY")) {
            return "{\"decision\":\"DENY\",\"quantity\":0,\"amountInr\":0,\"rationale\":\"the order is outside the refund window\"}";
        }
        Matcher price = UNIT_PRICE.matcher(system);
        Matcher qty = REQUESTED.matcher(system);
        BigDecimal unit = price.find() ? new BigDecimal(price.group(1)) : BigDecimal.ZERO;
        int quantity = qty.find() ? Integer.parseInt(qty.group(1)) : 1;
        BigDecimal paid = unit.multiply(BigDecimal.valueOf(quantity));
        BigDecimal amount = feedback ? paid : paid.multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP);
        return "{\"decision\":\"APPROVE\",\"quantity\":" + quantity + ",\"amountInr\":" + amount.toPlainString()
                + ",\"rationale\":\"" + (feedback ? "refund of the price paid for a returned item" : "full refund including 18% GST") + "\"}";
    }

    private static AssistantMessage text(String s) {
        return new AssistantMessage(s);
    }

    private AssistantMessage toolCall(String name, String args) {
        return AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + toolCallIds.incrementAndGet(), "function", name, args))).build();
    }
}
