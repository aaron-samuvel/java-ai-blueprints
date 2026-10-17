package io.github.aaronsamuvel.blueprints.lifecycle.examples;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * Pillar 3 (Advisor API): a minimal around-advice on every model call. It records request size
 * and latency, the same hook later used for guardrails, FinOps budgets and PII tokenisation.
 */
public class AuditTrailAdvisor implements CallAdvisor {

    public record AuditEntry(int promptChars, int responseChars, long millis) {}

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long start = System.nanoTime();
        int in = request.prompt().getContents().length();
        ChatClientResponse response = chain.nextCall(request);
        int out = 0;
        if (response.chatResponse() != null && response.chatResponse().getResult() != null) {
            String t = response.chatResponse().getResult().getOutput().getText();
            out = t == null ? 0 : t.length();
        }
        entries.add(new AuditEntry(in, out, (System.nanoTime() - start) / 1_000_000));
        return response;
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public String getName() {
        return "AuditTrailAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
