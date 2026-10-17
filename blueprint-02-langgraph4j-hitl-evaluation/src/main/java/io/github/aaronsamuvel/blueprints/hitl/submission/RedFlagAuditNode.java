package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;

/** Integrity audit: falsified history, plagiarism, leaked credentials, missing tests. */
public final class RedFlagAuditNode implements NodeAction<ReviewWorkflowState> {
    public static final String DIMENSION = "red-flag-audit";

    private final ChatClient chat;

    public RedFlagAuditNode(ChatClient chat) {
        this.chat = chat;
    }

    @Override
    public Map<String, Object> apply(ReviewWorkflowState s) {
        RedFlagAudit audit = chat.prompt()
                .system("TASK: red-flag-audit\nList integrity concerns: inconsistent dates, plagiarism, credentials in code, "
                        + "untested code. Prefix a concern with CRITICAL: when it alone should block automation.")
                .user(SubmissionPrompts.profileAndDocument(s))
                .call().entity(RedFlagAudit.class);
        List<String> flags = audit.redFlags() == null ? List.of() : new ArrayList<>(audit.redFlags());
        DimensionScore score = new DimensionScore(DIMENSION, Math.max(0, Math.min(100, audit.score())),
                flags.isEmpty() ? "no integrity concerns" : flags.size() + " concern(s)");
        return Map.of(ReviewWorkflowState.DIMENSION_SCORES, List.of(score),
                ReviewWorkflowState.RED_FLAGS, flags,
                ReviewWorkflowState.NODE_LOG, List.of(DIMENSION + " on " + (Thread.currentThread().isVirtual() ? "virtual" : "platform") + " thread"));
    }
}
