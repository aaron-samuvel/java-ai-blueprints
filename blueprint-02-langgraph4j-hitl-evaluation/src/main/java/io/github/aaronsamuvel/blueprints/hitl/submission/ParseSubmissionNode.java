package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.util.List;
import java.util.Map;

import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import tools.jackson.databind.json.JsonMapper;

/** Extracts a {@link SubmissionProfile} from the raw document text and stores it as JSON. */
public final class ParseSubmissionNode implements NodeAction<ReviewWorkflowState> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ChatClient chat;

    public ParseSubmissionNode(ChatClient chat) {
        this.chat = chat;
    }

    @Override
    public Map<String, Object> apply(ReviewWorkflowState s) {
        SubmissionProfile profile = chat.prompt()
                .system("TASK: parse-submission\nExtract a structured profile. Use only facts stated in the document.")
                .user(SubmissionPrompts.document(s.rawDocumentText()))
                .call().entity(SubmissionProfile.class);
        return Map.of(ReviewWorkflowState.PARSED_PROFILE_JSON, JSON.writeValueAsString(profile),
                ReviewWorkflowState.NODE_LOG, List.of("parseSubmission"));
    }
}
