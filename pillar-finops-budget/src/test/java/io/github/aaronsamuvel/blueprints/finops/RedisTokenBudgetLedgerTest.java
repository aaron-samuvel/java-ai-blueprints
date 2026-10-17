package io.github.aaronsamuvel.blueprints.finops;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aaronsamuvel.blueprints.finops.redis.RedisTokenBudgetLedger;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline: the Redis ledger's logic over an in-memory gateway. The Lua scripts themselves run in RedisTokenBudgetLedgerIT (-Pit). */
class RedisTokenBudgetLedgerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void concurrentReservationsNeverOversubscribe() throws Exception {
        RedisTokenBudgetLedger ledger = new RedisTokenBudgetLedger(new InMemoryRedisBudgetGateway(), 1_000, clock);
        AtomicInteger ok = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 32; i++) jobs.add(() -> {
            try { ledger.reserve("acme", 100); ok.incrementAndGet(); } catch (BudgetExceededException e) { /* refused */ }
            return null;
        });
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        }
        assertThat(ok.get()).isEqualTo(10);
        assertThat(ledger.snapshot("acme").reserved()).isEqualTo(1_000);
    }

    @Test
    void commitReleaseAndDoubleSettle() {
        RedisTokenBudgetLedger ledger = new RedisTokenBudgetLedger(new InMemoryRedisBudgetGateway(), 1_000, clock);
        ledger.setDailyLimit("acme", 500);
        var r = ledger.reserve("acme", 300);
        ledger.commit(r, 120);
        assertThat(ledger.snapshot("acme")).isEqualTo(new TokenBudgetLedger.Snapshot("acme", 500, 0, 120));
        assertThatThrownBy(() -> ledger.commit(r, 1)).isInstanceOf(IllegalStateException.class);
        ledger.release(ledger.reserve("acme", 380));
        assertThatThrownBy(() -> ledger.reserve("acme", 381))
                .isInstanceOfSatisfying(BudgetExceededException.class, e -> assertThat(e.getMessage()).contains("available 380"));
    }

    @Test
    void advisorOnTheRedisLedgerBlocksBeforeTheModel() {
        RedisTokenBudgetLedger ledger = new RedisTokenBudgetLedger(new InMemoryRedisBudgetGateway(), 1_000, clock);
        ledger.setDailyLimit("tiny", 50);
        AtomicInteger calls = new AtomicInteger();
        ChatClient client = ChatClient.builder(new StubChatModel(p -> { calls.incrementAndGet(); return "ok"; }))
                .defaultAdvisors(new FinOpsBudgetAdvisor(ledger, new TokenEstimator(), 256, PriceList.FREE)).build();
        assertThatThrownBy(() -> client.prompt().user("hi").advisors(a -> a.param(FinOpsBudgetAdvisor.TENANT_KEY, "tiny")).call().content())
                .isInstanceOf(BudgetExceededException.class);
        assertThat(calls.get()).isZero();
    }
}
