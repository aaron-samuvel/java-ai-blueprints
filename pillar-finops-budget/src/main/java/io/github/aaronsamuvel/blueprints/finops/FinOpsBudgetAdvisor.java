package io.github.aaronsamuvel.blueprints.finops;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

/**
 * Reserve, call, commit around every model call:
 * <ul>
 *   <li><b>reserve:</b> estimated prompt tokens (with margin) + {@code maxOutputTokens}. If over
 *       budget, throws {@link BudgetExceededException} and the model is never called.</li>
 *   <li><b>call:</b> continue the advisor chain.</li>
 *   <li><b>commit:</b> provider-reported usage if present, otherwise a local token count of the
 *       prompt and answer. If the call fails, the reservation is released.</li>
 * </ul>
 * The tenant is read from the advisor param {@link #TENANT_KEY} (default "default"). Runs right
 * after the PII advisor, so it counts the tokenised prompt that is actually sent.
 */
public class FinOpsBudgetAdvisor implements CallAdvisor {

    public static final String TENANT_KEY = "finops.tenant";
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private final TokenBudgetAuthority ledger;
    private final TokenEstimator estimator;
    private final long maxOutputTokens;
    private final PriceList prices;
    private final List<UsageRecord> records = new CopyOnWriteArrayList<>();

    public FinOpsBudgetAdvisor(TokenBudgetAuthority ledger, TokenEstimator estimator, long maxOutputTokens, PriceList prices) {
        this.ledger = ledger;
        this.estimator = estimator;
        this.maxOutputTokens = maxOutputTokens;
        this.prices = prices;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        String tenant = String.valueOf(request.context().getOrDefault(TENANT_KEY, "default"));
        String promptText = request.prompt().getContents();
        long estimate = estimator.withMargin(promptText) + maxOutputTokens;

        TokenBudgetLedger.Reservation reservation = ledger.reserve(tenant, estimate); // may throw: no call made
        ChatClientResponse response;
        try {
            response = chain.nextCall(request);
        } catch (RuntimeException e) {
            ledger.release(reservation);
            throw e;
        }

        long prompt, completion;
        boolean fromProvider;
        ChatResponse cr = response.chatResponse();
        Usage usage = cr == null || cr.getMetadata() == null ? null : cr.getMetadata().getUsage();
        if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
            prompt = nz(usage.getPromptTokens());
            completion = nz(usage.getCompletionTokens());
            fromProvider = true;
        } else {
            prompt = estimator.count(promptText);
            String out = cr == null || cr.getResult() == null ? "" : cr.getResult().getOutput().getText();
            completion = estimator.count(out);
            fromProvider = false;
        }
        ledger.commit(reservation, prompt + completion);
        records.add(new UsageRecord(tenant, estimate, prompt, completion, fromProvider, prices.cost(prompt, completion)));
        return response;
    }

    private static long nz(Integer i) {
        return i == null ? 0 : i;
    }

    public List<UsageRecord> records() {
        return List.copyOf(records);
    }

    @Override
    public String getName() {
        return "FinOpsBudgetAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
