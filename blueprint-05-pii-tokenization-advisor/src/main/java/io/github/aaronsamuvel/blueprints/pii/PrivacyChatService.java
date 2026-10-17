package io.github.aaronsamuvel.blueprints.pii;

import io.github.aaronsamuvel.blueprints.pii.advisor.PiiTokenizationAdvisor;
import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.pii.core.TokenVault;
import io.github.aaronsamuvel.blueprints.pii.tools.CustomerTools;
import io.github.aaronsamuvel.blueprints.pii.tools.PrivacyEnforcingToolCallback;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/** Chat entry point: every call goes through the PII advisor and privacy-enforced tools. */
@Service
public class PrivacyChatService {

    private final ChatClient chatClient;
    private final PiiTokenizer tokenizer;
    private final CustomerTools tools;

    public PrivacyChatService(ChatModel chatModel, PiiTokenizer tokenizer, CustomerTools tools) {
        this.tokenizer = tokenizer;
        this.tools = tools;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("You are a helpful support assistant. Values like <<EMAIL_1>> are "
                        + "placeholders for personal data; repeat them verbatim, never guess them.")
                .defaultAdvisors(new PiiTokenizationAdvisor(tokenizer))
                .build();
    }

    public String chat(String message) {
        TokenVault vault = new TokenVault(); // one vault per conversation turn
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(PiiTokenizationAdvisor.VAULT_KEY, vault))
                .toolCallbacks(PrivacyEnforcingToolCallback.wrap(tokenizer, vault, tools))
                .call()
                .content();
    }
}
