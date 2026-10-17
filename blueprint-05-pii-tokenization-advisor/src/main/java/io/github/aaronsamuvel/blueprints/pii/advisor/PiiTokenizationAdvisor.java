package io.github.aaronsamuvel.blueprints.pii.advisor;

import java.util.ArrayList;
import java.util.List;

import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.pii.core.TokenVault;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

/**
 * Zero-trust boundary: tokenises PII in system and user messages before the call continues down
 * the chain (so later advisors, logs and the model only see tokens) and detokenises the model's
 * answer on the way back. Runs first ({@link Ordered#HIGHEST_PRECEDENCE}).
 */
public class PiiTokenizationAdvisor implements CallAdvisor {

    public static final String VAULT_KEY = "pii.vault";

    private final PiiTokenizer tokenizer;

    public PiiTokenizationAdvisor(PiiTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        TokenVault vault = request.context().get(VAULT_KEY) instanceof TokenVault v ? v : new TokenVault();

        List<Message> safe = new ArrayList<>();
        for (Message m : request.prompt().getInstructions()) {
            safe.add(switch (m) {
                case UserMessage u -> new UserMessage(tokenizer.tokenize(u.getText(), vault));
                case SystemMessage s -> new SystemMessage(tokenizer.tokenize(s.getText(), vault));
                default -> m;
            });
        }
        ChatClientRequest safeRequest = request.mutate()
                .prompt(new Prompt(safe, request.prompt().getOptions()))
                .context(VAULT_KEY, vault)
                .build();

        ChatClientResponse response = chain.nextCall(safeRequest);
        ChatResponse cr = response.chatResponse();
        if (cr == null) return response;

        List<Generation> restored = cr.getResults().stream()
                .map(g -> new Generation(
                        new AssistantMessage(tokenizer.detokenize(g.getOutput().getText(), vault)),
                        g.getMetadata()))
                .toList();
        return response.mutate().chatResponse(new ChatResponse(restored, cr.getMetadata())).build();
    }

    @Override
    public String getName() {
        return "PiiTokenizationAdvisor";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
