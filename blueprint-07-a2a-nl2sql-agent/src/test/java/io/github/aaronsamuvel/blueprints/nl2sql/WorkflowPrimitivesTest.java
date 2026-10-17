package io.github.aaronsamuvel.blueprints.nl2sql;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aaronsamuvel.blueprints.nl2sql.mesh.LocalAgentRegistry;
import io.github.aaronsamuvel.blueprints.nl2sql.mesh.MeshAgentCard;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.AgentStep;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ContextCompactor;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.LoopAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.LoopGuardException;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ParallelAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.SequentialAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.WorkflowContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowPrimitivesTest {

    private static AgentStep sleepy(String key) {
        return c -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            c.put(key, true);
        };
    }

    @Test
    void parallelBranchesRunConcurrentlyOnVirtualThreads() {
        WorkflowContext ctx = new WorkflowContext(10);
        long t0 = System.nanoTime();
        new ParallelAgent("p", Duration.ofSeconds(5), sleepy("a"), sleepy("b"), sleepy("c")).run(ctx);
        assertThat((System.nanoTime() - t0) / 1_000_000).isLessThan(800);
        assertThat(ctx.get("a")).contains(true);
        assertThat(ctx.get("c")).contains(true);
        assertThatThrownBy(() -> new ParallelAgent("slow", Duration.ofMillis(50), sleepy("x")).run(new WorkflowContext(1)))
                .isInstanceOf(LoopGuardException.class);
    }

    @Test
    void loopStopsWhenDoneOrAtTheCapAndToolBudgetTrips() {
        AtomicInteger n = new AtomicInteger();
        WorkflowContext ctx = new WorkflowContext(10);
        new LoopAgent("l", c -> c.put("n", n.incrementAndGet()), c -> c.<Integer>require("n") == 2, 5, true, null).run(ctx);
        assertThat(ctx.<Integer>require("l.iterations")).isEqualTo(2);
        assertThatThrownBy(() -> new LoopAgent("never", c -> { }, c -> false, 3, true, null).run(new WorkflowContext(10)))
                .isInstanceOf(LoopGuardException.class).hasMessageContaining("3 iterations");
        WorkflowContext budget = new WorkflowContext(2);
        assertThatThrownBy(() -> new LoopAgent("tools", c -> c.countToolCall("sql"), c -> false, 5, false, null).run(budget))
                .isInstanceOf(LoopGuardException.class).hasMessageContaining("tool-call budget of 2");
    }

    @Test
    void sequentialCompactsTheTranscriptAboveTheTokenThreshold() {
        WorkflowContext ctx = new WorkflowContext(10);
        AgentStep big = c -> c.log("result table:\n" + "row ".repeat(100));
        new SequentialAgent("s", new ContextCompactor(50, 1), big, big, big).run(ctx);
        assertThat(ctx.compactions()).isPositive();
        assertThat(ctx.transcript()).hasSize(2);
        assertThat(ctx.transcript().get(0)).startsWith(ContextCompactor.SUMMARY_PREFIX).doesNotContain("row row");
    }

    @Test
    void registryExpiresCardsAndRanksBySla() {
        Instant[] now = {Instant.parse("2026-10-17T10:00:00Z")};
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now[0]; }
        };
        LocalAgentRegistry r = new LocalAgentRegistry(Duration.ofSeconds(30), clock);
        r.register(new MeshAgentCard("slow", "", "http://a/a2a", "1", List.of("x"), Map.of(), Map.of(), new MeshAgentCard.SlaMetrics(10, 1.0, 900)));
        r.register(new MeshAgentCard("fast", "", "http://b/a2a", "1", List.of("x"), Map.of(), Map.of(), new MeshAgentCard.SlaMetrics(10, 1.0, 100)));
        assertThat(r.discover("x")).extracting(MeshAgentCard::name).containsExactly("fast", "slow");
        now[0] = now[0].plusSeconds(31);
        assertThat(r.discover("x")).isEmpty();
    }
}
