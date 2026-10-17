package io.github.aaronsamuvel.blueprints.finops;

import java.util.HashMap;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.finops.redis.RedisBudgetGateway;

/** Offline fake with the Lua scripts' semantics; one lock stands in for Redis's single-threaded script execution. */
class InMemoryRedisBudgetGateway implements RedisBudgetGateway {

    private final Map<String, long[]> budgets = new HashMap<>();
    private final Map<String, Long> reservations = new HashMap<>();

    private long[] init(String key, long defaultLimit) {
        return budgets.computeIfAbsent(key, k -> new long[] {defaultLimit, 0, 0});
    }

    @Override
    public synchronized long reserve(String budgetKey, String reservationKey, long tokens, long defaultLimit, long ttlSeconds) {
        long[] b = init(budgetKey, defaultLimit);
        if (b[2] + b[1] + tokens > b[0]) return Math.max(0, b[0] - b[2] - b[1]);
        b[1] += tokens;
        reservations.put(reservationKey, tokens);
        return -1;
    }

    @Override
    public synchronized boolean settle(String reservationKey, String budgetKey, long actual) {
        Long held = reservations.remove(reservationKey);
        if (held == null) return false;
        long[] b = budgets.get(budgetKey);
        b[1] -= held;
        b[2] += actual;
        return true;
    }

    @Override
    public synchronized long[] snapshot(String budgetKey, long defaultLimit, long ttlSeconds) {
        return init(budgetKey, defaultLimit).clone();
    }

    @Override
    public synchronized void setLimit(String budgetKey, long limit, long ttlSeconds) {
        init(budgetKey, limit)[0] = limit;
    }
}
