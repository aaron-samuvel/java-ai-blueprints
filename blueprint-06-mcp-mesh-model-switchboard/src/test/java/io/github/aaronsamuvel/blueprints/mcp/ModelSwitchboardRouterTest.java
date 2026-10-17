package io.github.aaronsamuvel.blueprints.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aaronsamuvel.blueprints.mcp.host.ModelSwitchboardRouter;
import io.github.aaronsamuvel.blueprints.mcp.host.ModelTarget;
import io.github.aaronsamuvel.blueprints.mcp.host.TaskProfile;
import io.github.aaronsamuvel.blueprints.mcp.host.TicketExtraction;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSwitchboardRouterTest {

    private static StubChatModel named(String name) {
        return new StubChatModel(p -> "[" + name + "] " + StubChatModel.lastText(p));
    }

    private static StubChatModel slow(String name, long ms) {
        return new StubChatModel(p -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "[" + name + "] late";
        });
    }

    private static ModelSwitchboardRouter router(StubChatModel fast, StubChatModel frontier, Duration cloudTimeout) {
        return new ModelSwitchboardRouter(Map.of(ModelTarget.LOCAL_SOVEREIGN, named("LOCAL"), ModelTarget.CLOUD_FAST, fast,
                ModelTarget.CLOUD_FRONTIER, frontier), cloudTimeout, Duration.ofMillis(1500), 0.5);
    }

    private static final String COMPLEX = "Design a migration strategy for our payments platform: compare the trade-off between "
            + "strangler-fig and big-bang, explain why each fails, and plan the rollout step by step.";

    @Test
    void routesOnPrivacyComplexityAndLatencyBudget() {
        try (var r = router(named("FAST"), named("FRONTIER"), Duration.ofSeconds(5))) {
            assertThat(r.decide(new TaskProfile("Email me at asha@example.com about my refund", false, null)).target()).isEqualTo(ModelTarget.LOCAL_SOVEREIGN);
            assertThat(r.decide(new TaskProfile("What is the refund window?", true, null)).target()).isEqualTo(ModelTarget.LOCAL_SOVEREIGN);
            assertThat(r.decide(new TaskProfile("Classify the intent of: where is my order", false, null)).target()).isEqualTo(ModelTarget.LOCAL_SOVEREIGN);
            assertThat(r.decide(new TaskProfile("Extract the order id from: order 4411 is late", false, null)).target()).isEqualTo(ModelTarget.CLOUD_FAST);
            var complex = r.decide(new TaskProfile(COMPLEX, false, Duration.ofSeconds(20)));
            assertThat(complex.target()).isEqualTo(ModelTarget.CLOUD_FRONTIER);
            assertThat(complex.complexity()).isGreaterThanOrEqualTo(0.5);
            var hurried = r.decide(new TaskProfile(COMPLEX, false, Duration.ofMillis(600)));
            assertThat(hurried.target()).isEqualTo(ModelTarget.CLOUD_FAST);
            assertThat(hurried.reason()).contains("latency budget");
        }
    }

    @Test
    void fallsBackToTheLocalModelWhenTheCloudTimesOut() {
        try (var r = router(slow("FAST", 3000), slow("FRONTIER", 3000), Duration.ofMillis(300))) {
            long t0 = System.nanoTime();
            var a = r.ask(new TaskProfile(COMPLEX, false, Duration.ofSeconds(20)));
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertThat(a.decision().target()).isEqualTo(ModelTarget.CLOUD_FRONTIER);
            assertThat(a.used()).isEqualTo(ModelTarget.LOCAL_SOVEREIGN);
            assertThat(a.fellBack()).isTrue();
            assertThat(a.fallbackReason()).contains("did not answer within 300 ms");
            assertThat(a.answer()).startsWith("[LOCAL]");
            assertThat(ms).isLessThan(2000);
        }
    }

    @Test
    void fallsBackWhenTheCloudFailsAndKeepsCloudAnswersWhenItWorks() {
        StubChatModel broken = new StubChatModel(p -> {
            throw new IllegalStateException("HTTP 529 overloaded");
        });
        try (var r = router(broken, broken, Duration.ofSeconds(2))) {
            var a = r.ask(new TaskProfile("Extract the order id from: order 4411 is late", false, null));
            assertThat(a.fellBack()).isTrue();
            assertThat(a.fallbackReason()).contains("overloaded");
        }
        try (var r = router(named("FAST"), named("FRONTIER"), Duration.ofSeconds(2))) {
            var a = r.ask(new TaskProfile("Extract the order id from: order 4411 is late", false, null));
            assertThat(a.fellBack()).isFalse();
            assertThat(a.answer()).startsWith("[FAST]");
        }
    }

    @Test
    void structuredOutputIsValidatedAndRetriedWithTheErrorFed() {
        List<String> prompts = new CopyOnWriteArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        StubChatModel flaky = new StubChatModel(p -> {
            prompts.add(p.getContents());
            return calls.incrementAndGet() == 1 ? "{\"category\":\"billing\",\"priority\":\"high\"" // truncated JSON
                    : "{\"category\":\"billing\",\"priority\":\"high\",\"summary\":\"charged twice\"}";
        });
        try (var r = new ModelSwitchboardRouter(Map.of(ModelTarget.LOCAL_SOVEREIGN, flaky), Duration.ofSeconds(2), Duration.ofMillis(1500), 0.5)) {
            TicketExtraction t = r.extract(new TaskProfile("I was charged twice", false, null), TicketExtraction.class, 3);
            assertThat(t).isEqualTo(new TicketExtraction("billing", "high", "charged twice"));
            assertThat(calls.get()).isEqualTo(2);
            assertThat(prompts.get(1).length()).isGreaterThan(prompts.get(0).length());
        }
    }
}
