package io.github.aaronsamuvel.blueprints.hitl.submission;

import org.springframework.ai.chat.client.ChatClient;

/** Team fit and governance: mentoring, reviews, documentation, compliance. */
public final class CultureAndGovernanceNode extends DimensionEvaluatorNode {
    public static final String DIMENSION = "culture-governance";

    public CultureAndGovernanceNode(ChatClient chat) {
        super(chat, DIMENSION, "mentoring, code review, documentation, governance and compliance, collaboration");
    }
}
