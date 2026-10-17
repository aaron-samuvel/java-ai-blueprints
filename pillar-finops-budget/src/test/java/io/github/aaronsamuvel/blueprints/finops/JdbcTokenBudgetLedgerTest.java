package io.github.aaronsamuvel.blueprints.finops;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTokenBudgetLedgerTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-17T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static SimpleDriverDataSource db(String name) {
        return new SimpleDriverDataSource(new org.h2.Driver(), "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
    }

    @Test
    void concurrentReservationsNeverOversubscribeTheBudget() throws Exception {
        JdbcTokenBudgetLedger ledger = new JdbcTokenBudgetLedger(db("finops1"), 1_000, new MutableClock()).createSchema();
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            jobs.add(() -> {
                try {
                    ledger.reserve("acme", 100);
                    ok.incrementAndGet();
                } catch (BudgetExceededException e) {
                    refused.incrementAndGet();
                }
                return null;
            });
        }
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        }
        assertThat(ok.get()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(22);
        assertThat(ledger.snapshot("acme").reserved()).isEqualTo(1_000);
    }

    @Test
    void commitChargesActualUsageReleaseReturnsEverythingAndBudgetsAreDaily() {
        MutableClock clock = new MutableClock();
        JdbcTokenBudgetLedger ledger = new JdbcTokenBudgetLedger(db("finops2"), 1_000, clock).createSchema();
        ledger.setDailyLimit("acme", 500);
        var r1 = ledger.reserve("acme", 300);
        ledger.commit(r1, 120);
        assertThat(ledger.snapshot("acme")).isEqualTo(new TokenBudgetLedger.Snapshot("acme", 500, 0, 120));
        var r2 = ledger.reserve("acme", 380);
        ledger.release(r2);
        assertThat(ledger.snapshot("acme").available()).isEqualTo(380);
        assertThatThrownBy(() -> ledger.commit(r1, 10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ledger.reserve("acme", 381)).isInstanceOf(BudgetExceededException.class);

        clock.now = clock.now.plusSeconds(86_400); // next UTC day: fresh budget with the tenant's limit
        assertThat(ledger.snapshot("acme")).isEqualTo(new TokenBudgetLedger.Snapshot("acme", 500, 0, 0));
        assertThat(ledger.snapshot("globex").limit()).isEqualTo(1_000);
    }

    @Test
    void advisorOnTheJdbcLedgerBlocksTheCallBeforeTheModelWhenExhausted() {
        JdbcTokenBudgetLedger ledger = new JdbcTokenBudgetLedger(db("finops3"), 1_000, new MutableClock()).createSchema();
        ledger.setDailyLimit("tiny", 50);
        AtomicInteger modelCalls = new AtomicInteger();
        StubChatModel model = new StubChatModel(p -> {
            modelCalls.incrementAndGet();
            return "ok";
        });
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(new FinOpsBudgetAdvisor(ledger, new TokenEstimator(), 256, PriceList.FREE)).build();
        assertThatThrownBy(() -> client.prompt().user("hello").advisors(a -> a.param(FinOpsBudgetAdvisor.TENANT_KEY, "tiny")).call().content())
                .isInstanceOf(BudgetExceededException.class);
        assertThat(modelCalls.get()).isZero();
        assertThat(client.prompt().user("hello").advisors(a -> a.param(FinOpsBudgetAdvisor.TENANT_KEY, "acme")).call().content()).isEqualTo("ok");
        assertThat(ledger.snapshot("acme").used()).isPositive();
        assertThat(ledger.snapshot("acme").reserved()).isZero();
    }
}
