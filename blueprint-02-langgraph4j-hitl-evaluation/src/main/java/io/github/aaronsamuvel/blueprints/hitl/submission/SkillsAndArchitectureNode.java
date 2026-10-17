package io.github.aaronsamuvel.blueprints.hitl.submission;

import org.springframework.ai.chat.client.ChatClient;

/** Technical depth: distributed systems, architecture patterns, the Spring and cloud stack. */
public final class SkillsAndArchitectureNode extends DimensionEvaluatorNode {
    public static final String DIMENSION = "skills-architecture";

    public SkillsAndArchitectureNode(ChatClient chat) {
        super(chat, DIMENSION, "architecture patterns, distributed systems, messaging, cloud-native delivery");
    }
}
