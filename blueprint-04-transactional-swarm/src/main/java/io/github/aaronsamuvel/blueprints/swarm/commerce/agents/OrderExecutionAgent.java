package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Places orders and answers order-status questions by calling {@link OrderTools} through Spring AI
 * tool calling. The model chooses the tool and its arguments; each tool runs in its own transaction.
 */
public class OrderExecutionAgent {

    public static final String NAME = "order-execution";
    public static final String SYSTEM = """
            TASK: order
            You are the order execution agent. Use the placeOrder tool to place orders and the orderStatus tool to
            look up an order. Never invent order ids or prices; report exactly what the tool returned.""";

    private final ChatClient chat;
    private final OrderTools tools;

    public OrderExecutionAgent(ChatClient chat, OrderTools tools) {
        this.chat = chat;
        this.tools = tools;
    }

    public AgentReply handle(Turn turn) {
        OrderTools.Session session = OrderTools.open(turn.key().userId());
        try {
            String answer = chat.prompt().system(SYSTEM).messages(turn.history()).user(turn.message()).tools(tools)
                    .call().content();
            return new AgentReply(NAME, answer, Map.of("toolCalls", session.calls()));
        } finally {
            OrderTools.close();
        }
    }
}
