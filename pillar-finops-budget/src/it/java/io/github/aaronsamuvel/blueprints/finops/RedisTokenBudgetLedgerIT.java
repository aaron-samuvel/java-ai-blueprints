package io.github.aaronsamuvel.blueprints.finops;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aaronsamuvel.blueprints.finops.redis.RedisTokenBudgetLedger;
import io.github.aaronsamuvel.blueprints.finops.redis.SpringDataRedisBudgetGateway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live: runs the Lua scripts against a real Redis (the ai-redis container on localhost:6379, or
 * REDIS_HOST/REDIS_PORT). Run with {@code mvn -Pit -pl pillar-finops-budget verify}.
 */
class RedisTokenBudgetLedgerIT {

    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;
    final String prefix = "finops-it-" + UUID.randomUUID();

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                System.getenv().getOrDefault("REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"))));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @Test
    void luaReservationIsAtomicUnderConcurrency() throws Exception {
        RedisTokenBudgetLedger ledger = new RedisTokenBudgetLedger(new SpringDataRedisBudgetGateway(redis), 1_000, Clock.systemUTC(), prefix);
        AtomicInteger ok = new AtomicInteger();
        List<Callable<Void>> jobs = new ArrayList<>();
        for (int i = 0; i < 32; i++) jobs.add(() -> {
            try { ledger.reserve("acme", 100); ok.incrementAndGet(); } catch (BudgetExceededException e) { /* refused */ }
            return null;
        });
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        }
        assertThat(ok.get()).isEqualTo(10);
        assertThat(ledger.snapshot("acme").reserved()).isEqualTo(1_000);
        redis.delete(redis.keys(prefix + ":*"));
    }

    @Test
    void commitMovesReservedToUsed() {
        RedisTokenBudgetLedger ledger = new RedisTokenBudgetLedger(new SpringDataRedisBudgetGateway(redis), 1_000, Clock.systemUTC(), prefix);
        ledger.setDailyLimit("acme", 500);
        ledger.commit(ledger.reserve("acme", 300), 120);
        assertThat(ledger.snapshot("acme")).isEqualTo(new TokenBudgetLedger.Snapshot("acme", 500, 0, 120));
        redis.delete(redis.keys(prefix + ":*"));
    }
}
