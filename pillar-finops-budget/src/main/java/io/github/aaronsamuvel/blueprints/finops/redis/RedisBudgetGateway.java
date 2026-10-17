package io.github.aaronsamuvel.blueprints.finops.redis;

/**
 * The four atomic Redis operations {@link RedisTokenBudgetLedger} needs. Each one is a single Lua
 * script in {@link SpringDataRedisBudgetGateway}, so a reservation can never interleave with
 * another. Tests use an in-memory fake with the same semantics.
 */
public interface RedisBudgetGateway {

    /** Reserve atomically. Returns -1 if granted, otherwise the tokens still available (>= 0). */
    long reserve(String budgetKey, String reservationKey, long tokens, long defaultLimit, long ttlSeconds);

    /** Settle a reservation: release it and add {@code actual} to used. Returns false if it was already settled. */
    boolean settle(String reservationKey, String budgetKey, long actual);

    /** {limit, reserved, used}; creates the budget with {@code defaultLimit} if it does not exist. */
    long[] snapshot(String budgetKey, long defaultLimit, long ttlSeconds);

    void setLimit(String budgetKey, long limit, long ttlSeconds);
}
