package io.github.aaronsamuvel.blueprints.lifecycle.examples;

import org.springframework.ai.chat.client.ChatClient;

/** Asks the model to fill an {@link IncidentTriage} record via Spring AI structured output. */
public class StructuredOutputExample {

    private final ChatClient chatClient;

    public StructuredOutputExample(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public IncidentTriage triage(String alertText) {
        return chatClient.prompt()
                .system("You are an SRE triage assistant. Classify the alert.")
                .user(alertText)
                .call()
                .entity(IncidentTriage.class);
    }
}
