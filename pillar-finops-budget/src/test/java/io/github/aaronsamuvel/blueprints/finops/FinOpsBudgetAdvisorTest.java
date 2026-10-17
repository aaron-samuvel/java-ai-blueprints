package io.github.aaronsamuvel.blueprints.finops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class FinOpsBudgetAdvisorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void commitsLocallyCountedTokensWhenProviderReportsNoUsage() {
        TokenBudgetLedger ledger = new TokenBudgetLedger(10_000);
        FinOpsBudgetAdvisor advisor = new FinOpsBudgetAdvisor(ledger, estimator, 100, PriceList.FREE);
        ChatClient client = ChatClient.builder(StubChatModel.echo()).defaultAdvisors(advisor).build();

        client.prompt().user("hello budget").call().content();

        UsageRecord rec = advisor.records().get(0);
        assertThat(rec.fromProvider()).isFalse();
        assertThat(rec.promptTokens()).isEqualTo(estimator.count("hello budget"));
        assertThat(rec.completionTokens()).isEqualTo(estimator.count("echo: hello budget"));
        var snap = ledger.snapshot("default");
        assertThat(snap.reserved()).isZero();             // reservation settled
        assertThat(snap.used()).isEqualTo(rec.totalTokens());
        assertThat(rec.reserved()).isGreaterThan(rec.totalTokens()); // estimate was a safe upper bound
    }

    @Test
    void prefersProviderUsageAndPricesIt() {
        ChatModel withUsage = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))),
                        ChatResponseMetadata.builder().usage(new DefaultUsage(120, 30)).build());
            }
        };
        TokenBudgetLedger ledger = new TokenBudgetLedger(10_000);
        PriceList prices = new PriceList(new BigDecimal("0.50"), new BigDecimal("1.50"));
        FinOpsBudgetAdvisor advisor = new FinOpsBudgetAdvisor(ledger, estimator, 100, prices);
        ChatClient.builder(withUsage).defaultAdvisors(advisor).build()
                .prompt().user("q").advisors(a -> a.param(FinOpsBudgetAdvisor.TENANT_KEY, "acme")).call().content();

        UsageRecord rec = advisor.records().get(0);
        assertThat(rec.tenant()).isEqualTo("acme");
        assertThat(rec.fromProvider()).isTrue();
        assertThat(rec.totalTokens()).isEqualTo(150);
        assertThat(rec.costInr()).isEqualByComparingTo("0.1050"); // 120*0.5/1000 + 30*1.5/1000
        assertThat(ledger.snapshot("acme").used()).isEqualTo(150);
    }

    @Test
    void overBudgetCallIsBlockedBeforeTheModelIsCalled() {
        StubChatModel model = StubChatModel.echo();
        TokenBudgetLedger ledger = new TokenBudgetLedger(50);
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new FinOpsBudgetAdvisor(ledger, estimator, 100, PriceList.FREE)).build();

        assertThatThrownBy(() -> client.prompt().user("any question").call().content())
                .isInstanceOf(BudgetExceededException.class);
        assertThat(model.received()).isEmpty();
        assertThat(ledger.snapshot("default").used()).isZero();
    }

    @Test
    void failedCallReleasesReservation() {
        ChatModel broken = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("provider down");
            }
        };
        TokenBudgetLedger ledger = new TokenBudgetLedger(1_000);
        ChatClient client = ChatClient.builder(broken)
                .defaultAdvisors(new FinOpsBudgetAdvisor(ledger, estimator, 100, PriceList.FREE)).build();

        assertThatThrownBy(() -> client.prompt().user("x").call().content()).hasMessageContaining("provider down");
        assertThat(ledger.snapshot("default")).isEqualTo(new TokenBudgetLedger.Snapshot("default", 1_000, 0, 0));
    }

    @Test
    void budgetRunsOutAfterRepeatedCalls() {
        TokenBudgetLedger ledger = new TokenBudgetLedger(300);
        StubChatModel model = StubChatModel.echo();
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new FinOpsBudgetAdvisor(ledger, estimator, 50, PriceList.FREE)).build();
        int ok = 0;
        try {
            for (int i = 0; i < 100; i++) {
                client.prompt().user("Summarise ticket " + i + " about a delayed refund").call().content();
                ok++;
            }
        } catch (BudgetExceededException expected) {
            // stop
        }
        assertThat(ok).isBetween(1, 99);
        assertThat(model.received()).hasSize(ok);
        assertThat(ledger.snapshot("default").used()).isLessThanOrEqualTo(300);
    }
}
