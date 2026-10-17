package io.github.aaronsamuvel.blueprints.finops.redis;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import io.github.aaronsamuvel.blueprints.finops.BudgetExceededException;
import io.github.aaronsamuvel.blueprints.finops.TokenBudgetAuthority;
import io.github.aaronsamuvel.blueprints.finops.TokenBudgetLedger;

/**
 * Redis budget authority (handbook Part IV, 10.1: "atomically reserve that quota in Redis").
 * Daily budgets per tenant under {@code finops:budget:{tenant}:{yyyy-mm-dd}} (a hash of limit,
 * reserved and used); each open reservation is {@code finops:res:{period}:{uuid}} with a TTL, so a
 * crashed caller's hold expires instead of leaking. Atomicity comes from Lua scripts.
 */
public class RedisTokenBudgetLedger implements TokenBudgetAuthority {

    private static final long TTL_SECONDS = 3 * 24 * 3600;

    private final RedisBudgetGateway gateway;
    private final long defaultDailyLimit;
    private final Clock clock;
    private final String prefix;

    public RedisTokenBudgetLedger(RedisBudgetGateway gateway, long defaultDailyLimit, Clock clock) {
        this(gateway, defaultDailyLimit, clock, "finops");
    }

    public RedisTokenBudgetLedger(RedisBudgetGateway gateway, long defaultDailyLimit, Clock clock, String prefix) {
        this.gateway = gateway;
        this.defaultDailyLimit = defaultDailyLimit;
        this.clock = clock;
        this.prefix = prefix;
    }

    private String period() {
        return LocalDate.now(clock).toString();
    }

    private String budgetKey(String tenant, String period) {
        return prefix + ":budget:" + tenant + ":" + period;
    }

    public void setDailyLimit(String tenant, long limit) {
        gateway.setLimit(budgetKey(tenant, period()), limit, TTL_SECONDS);
    }

    @Override
    public TokenBudgetLedger.Reservation reserve(String tenant, long tokens) {
        if (tokens <= 0) throw new IllegalArgumentException("tokens must be positive");
        String period = period();
        String id = period + ":" + UUID.randomUUID();
        long result = gateway.reserve(budgetKey(tenant, period), prefix + ":res:" + id, tokens, defaultDailyLimit, TTL_SECONDS);
        if (result >= 0) throw new BudgetExceededException(tenant, tokens, result);
        return new TokenBudgetLedger.Reservation(id, tenant, tokens);
    }

    @Override
    public void commit(TokenBudgetLedger.Reservation r, long actualTokens) {
        if (!settle(r, Math.max(0, actualTokens))) throw new IllegalStateException("reservation already settled: " + r.id());
    }

    @Override
    public void release(TokenBudgetLedger.Reservation r) {
        settle(r, 0);
    }

    private boolean settle(TokenBudgetLedger.Reservation r, long actual) {
        String period = r.id().substring(0, r.id().indexOf(':'));
        return gateway.settle(prefix + ":res:" + r.id(), budgetKey(r.tenant(), period), actual);
    }

    @Override
    public TokenBudgetLedger.Snapshot snapshot(String tenant) {
        long[] s = gateway.snapshot(budgetKey(tenant, period()), defaultDailyLimit, TTL_SECONDS);
        return new TokenBudgetLedger.Snapshot(tenant, s[0], s[1], s[2]);
    }
}
