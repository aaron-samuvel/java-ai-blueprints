package io.github.aaronsamuvel.blueprints.pii.guardrails.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiAnalyzerSpi;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiSpan;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacySession;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyTokenVault;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.core.Ordered;

/**
 * The privacy boundary in front of the {@code ChatModel} (handbook Part III, Blueprint 5,
 * "PrivacyProtectionCallAdvisor implements CallAdvisor").
 * <p>On the way in it tokenises user, system, assistant (including tool-call arguments) and
 * tool-response messages, and the retrieved RAG documents in the request context. On the way out
 * it applies the session's {@code ResponsePolicy} to the final answer. Answers that are tool calls
 * pass through untouched: their placeholders are restored, per policy, by the tool guard.
 * <p>Order it <em>after</em> (inner to) the RAG, memory and tool-calling advisors so it sees every
 * model call, including each round of the tool loop. Without a {@link PrivacySession} in the
 * context it refuses the call (fails closed).
 */
public class PrivacyProtectionCallAdvisor implements CallAdvisor {

    public static final int DEFAULT_ORDER = Ordered.LOWEST_PRECEDENCE - 1000;

    private final PiiAnalyzerSpi analyzer;
    private final String detokenizeAuthority;
    private final int order;

    public PrivacyProtectionCallAdvisor(PiiAnalyzerSpi analyzer, String detokenizeAuthority, int order) {
        this.analyzer = analyzer;
        this.detokenizeAuthority = detokenizeAuthority;
        this.order = order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (!(request.context().get(PrivacySession.CONTEXT_KEY) instanceof PrivacySession session)) {
            throw new IllegalStateException("No PrivacySession in the advisor context; refusing to call the model");
        }
        PrivacyTokenVault vault = session.vault();

        List<Message> safe = new ArrayList<>();
        for (Message m : request.prompt().getInstructions()) {
            Message s = sanitize(m, vault);
            safe.add(s);
            session.audit().modelInput(describe(s));
        }
        ChatClientRequest.Builder next = request.mutate().prompt(new Prompt(safe, request.prompt().getOptions()));
        if (request.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT) instanceof List<?> docs) {
            next.context(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, docs.stream()
                    .map(d -> d instanceof Document doc ? sanitize(doc, vault) : d).toList());
        }

        ChatClientResponse response = chain.nextCall(next.build());
        ChatResponse cr = response.chatResponse();
        if (cr == null || cr.getResults().stream().anyMatch(g -> g.getOutput().hasToolCalls())) {
            return response;
        }
        List<Generation> out = cr.getResults().stream()
                .map(g -> new Generation(new AssistantMessage(applyPolicy(g.getOutput().getText(), session)), g.getMetadata()))
                .toList();
        return response.mutate().chatResponse(new ChatResponse(out, cr.getMetadata())).build();
    }

    String applyPolicy(String answer, PrivacySession session) {
        if (answer == null) return null;
        PrivacyTokenVault vault = session.vault();
        List<PiiSpan> rawSpans = analyzer.analyze(answer);
        int rawValues = rawSpans.size() + (int) vault.rawValuesIn(answer).stream()
                .filter(e -> rawSpans.stream().noneMatch(s -> s.text().equals(e.value()))).count();
        if (rawValues > 0) {
            session.audit().note("model answer contained " + rawValues + " unprotected PII value(s): "
                    + rawSpans.stream().map(s -> s.type().name()).distinct().collect(Collectors.joining(", ")));
        }
        return switch (session.policy()) {
            case BLOCK_ON_LEAK -> {
                if (rawValues > 0) {
                    session.audit().block();
                    yield "[blocked: the answer contained unprotected personal data]";
                }
                yield answer;
            }
            case REDACT -> vault.redactPlaceholders(vault.tokenize(answer, analyzer));
            case DETOKENIZE_FOR_AUTHORIZED_USER -> session.callerHas(detokenizeAuthority)
                    ? vault.restoreAll(answer).text()
                    : vault.redactPlaceholders(vault.tokenize(answer, analyzer));
        };
    }

    private Message sanitize(Message m, PrivacyTokenVault vault) {
        return switch (m) {
            case UserMessage u -> UserMessage.builder().text(vault.tokenize(u.getText(), analyzer)).media(u.getMedia())
                    .metadata(u.getMetadata()).build();
            case SystemMessage s -> new SystemMessage(vault.tokenize(s.getText(), analyzer));
            case AssistantMessage a -> AssistantMessage.builder()
                    .content(vault.tokenize(a.getText(), analyzer))
                    .toolCalls(a.getToolCalls().stream().map(tc -> new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(),
                            vault.tokenize(tc.arguments(), analyzer))).toList())
                    .properties(a.getMetadata())
                    .build();
            case ToolResponseMessage t -> ToolResponseMessage.builder()
                    .responses(t.getResponses().stream().map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(),
                            vault.tokenize(r.responseData(), analyzer))).toList())
                    .metadata(t.getMetadata())
                    .build();
            default -> m;
        };
    }

    private Document sanitize(Document d, PrivacyTokenVault vault) {
        return Document.builder().id(d.getId()).text(vault.tokenize(d.getText(), analyzer)).metadata(d.getMetadata()).build();
    }

    static String describe(Message m) {
        return switch (m) {
            case AssistantMessage a when a.hasToolCalls() -> "ASSISTANT: " + a.getText() + " tool calls " + a.getToolCalls().stream()
                    .map(tc -> tc.name() + tc.arguments()).toList();
            case ToolResponseMessage t -> "TOOL: " + t.getResponses().stream().map(r -> r.name() + " -> " + r.responseData()).toList();
            default -> m.getMessageType() + ": " + m.getText();
        };
    }

    @Override
    public String getName() {
        return "PrivacyProtectionCallAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }
}
