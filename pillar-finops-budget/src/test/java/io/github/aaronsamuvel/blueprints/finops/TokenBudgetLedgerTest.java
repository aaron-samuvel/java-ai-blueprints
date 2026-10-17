package io.github.aaronsamuvel.blueprints.finops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TokenBudgetLedgerTest {

    @Test
    void reserveCommitRelease() {
        TokenBudgetLedger l = new TokenBudgetLedger(1000);
        var r = l.reserve("acme", 400);
        assertThat(l.snapshot("acme").reserved()).isEqualTo(400);
        assertThat(l.snapshot("acme").available()).isEqualTo(600);
        l.commit(r, 250);
        assertThat(l.snapshot("acme")).isEqualTo(new TokenBudgetLedger.Snapshot("acme", 1000, 0, 250));
        var r2 = l.reserve("acme", 100);
        l.release(r2);
        assertThat(l.snapshot("acme").available()).isEqualTo(750);
    }

    @Test
    void reservationsCountAgainstBudgetBeforeCommit() {
        TokenBudgetLedger l = new TokenBudgetLedger(500);
        l.reserve("t", 300);
        assertThatThrownBy(() -> l.reserve("t", 300))
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining("requested 300, available 200");
    }

    @Test
    void tenantsAreIsolatedAndLimitsConfigurable() {
        TokenBudgetLedger l = new TokenBudgetLedger(100);
        l.setLimit("big", 10_000);
        l.reserve("big", 5_000);
        assertThatThrownBy(() -> l.reserve("small", 101)).isInstanceOf(BudgetExceededException.class);
    }

    @Test
    void doubleCommitIsRejected() {
        TokenBudgetLedger l = new TokenBudgetLedger(100);
        var r = l.reserve("t", 10);
        l.commit(r, 10);
        assertThatThrownBy(() -> l.commit(r, 10)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void concurrentReservationsNeverOverspend() throws Exception {
        TokenBudgetLedger l = new TokenBudgetLedger(10_000);
        AtomicInteger granted = new AtomicInteger();
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                exec.submit(() -> {
                    try {
                        l.reserve("t", 30);
                        granted.incrementAndGet();
                    } catch (BudgetExceededException ignored) {
                        // expected once the budget is gone
                    }
                });
            }
        }
        assertThat(granted.get()).isEqualTo(333); // floor(10000 / 30)
        assertThat(l.snapshot("t").reserved()).isEqualTo(9_990);
    }
}
