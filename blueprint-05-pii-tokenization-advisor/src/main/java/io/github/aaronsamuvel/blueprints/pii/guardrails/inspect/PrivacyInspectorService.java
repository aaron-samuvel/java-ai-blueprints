package io.github.aaronsamuvel.blueprints.pii.guardrails.inspect;

import java.util.ArrayList;
import java.util.List;

import io.github.aaronsamuvel.blueprints.pii.guardrails.advisor.PrivacyProtectionCallAdvisor;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.CompositePiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiSpan;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.BankingTools;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.PrivacyToolGuard;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacySession;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.ResponsePolicy;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Runs one request through the full guarded pipeline against {@link InspectorChatModel}:
 * RAG documents (via Spring AI's {@code RetrievalAugmentationAdvisor}), the tool-calling loop,
 * the {@link PrivacyProtectionCallAdvisor} and the {@link PrivacyToolGuard}. Returns the audit.
 */
public class PrivacyInspectorService {

    public record InspectRequest(String message, List<String> documents, ResponsePolicy responsePolicy) {}

    static final String SYSTEM = "You are a banking assistant. Values such as <PII:ACCOUNT_1> stand for protected "
            + "personal data; pass them to tools unchanged and never guess the real value.";

    private final CompositePiiAnalyzer analyzer;
    private final PrivacyToolGuard guard;
    private final BankingTools tools;
    private final String detokenizeAuthority;

    public PrivacyInspectorService(CompositePiiAnalyzer analyzer, PrivacyToolGuard guard, BankingTools tools, String detokenizeAuthority) {
        this.analyzer = analyzer;
        this.guard = guard;
        this.tools = tools;
        this.detokenizeAuthority = detokenizeAuthority;
    }

    public InspectionReport inspect(InspectRequest request, Authentication caller) {
        if (request.message() == null || request.message().isBlank()) throw new IllegalArgumentException("message is required");
        ResponsePolicy policy = request.responsePolicy() == null ? ResponsePolicy.DETOKENIZE_FOR_AUTHORIZED_USER : request.responsePolicy();
        List<String> docs = request.documents() == null ? List.of() : request.documents();
        PrivacySession session = PrivacySession.start(caller, policy);
        InspectorChatModel model = new InspectorChatModel();

        List<Advisor> advisors = new ArrayList<>();
        if (!docs.isEmpty()) {
            List<Document> retrieved = docs.stream().map(Document::new).toList();
            advisors.add(RetrievalAugmentationAdvisor.builder().documentRetriever(q -> retrieved).order(100).build());
        }
        advisors.add(ToolCallingAdvisor.builder().advisorOrder(200).build());
        advisors.add(new PrivacyProtectionCallAdvisor(analyzer, detokenizeAuthority, 300));

        String answer = ChatClient.builder(model).defaultSystem(SYSTEM).defaultAdvisors(advisors).build()
                .prompt()
                .user(request.message())
                .advisors(a -> a.param(PrivacySession.CONTEXT_KEY, session))
                .toolCallbacks(guard.authorize(session, List.of(MethodToolCallbackProvider.builder().toolObjects(tools).build().getToolCallbacks())))
                .call()
                .content();

        List<InspectionReport.DetectedEntity> detections = new ArrayList<>();
        List<String> rawTexts = new ArrayList<>(docs);
        rawTexts.add(0, request.message());
        for (String text : rawTexts) {
            for (PiiSpan s : analyzer.analyze(text)) {
                detections.add(new InspectionReport.DetectedEntity(s.type(), s.analyzer(), session.vault().placeholderFor(s.type(), s.text())));
            }
        }

        List<List<String>> received = model.received();
        List<PiiEntityType> rawSeen = new ArrayList<>();
        List<PiiEntityType> unprotected = new ArrayList<>();
        for (List<String> call : received) {
            for (String text : call) {
                session.vault().rawValuesIn(text).forEach(e -> rawSeen.add(e.type()));
                analyzer.analyze(text).forEach(s -> unprotected.add(s.type()));
            }
        }
        var verdict = new InspectionReport.BoundaryVerdict(session.vault().size(), received.size(),
                rawSeen.stream().distinct().toList(), unprotected.stream().distinct().toList(),
                rawSeen.isEmpty() && unprotected.isEmpty());
        List<String> authorities = caller == null ? List.of()
                : caller.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return new InspectionReport(request.message(), docs, authorities, policy, detections,
                session.audit().toolsOffered(), session.audit().toolsHidden(), received, model.answers(),
                session.audit().disclosures(), answer, session.audit().blocked(), session.audit().notes(), verdict);
    }
}
