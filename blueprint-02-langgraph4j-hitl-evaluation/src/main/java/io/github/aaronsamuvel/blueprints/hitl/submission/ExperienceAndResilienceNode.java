package io.github.aaronsamuvel.blueprints.hitl.submission;

import org.springframework.ai.chat.client.ChatClient;

/** Operational track record: incidents handled, resilience patterns, production ownership. */
public final class ExperienceAndResilienceNode extends DimensionEvaluatorNode {
    public static final String DIMENSION = "experience-resilience";

    public ExperienceAndResilienceNode(ChatClient chat) {
        super(chat, DIMENSION, "years in production, incident response, on-call, resilience engineering, postmortems");
    }
}
