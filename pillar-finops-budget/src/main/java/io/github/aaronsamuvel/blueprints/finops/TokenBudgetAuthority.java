package io.github.aaronsamuvel.blueprints.finops;

/**
 * The budget authority behind {@link FinOpsBudgetAdvisor}: reserve before the call, then commit or
 * release. Implementations are {@link TokenBudgetLedger} (in-memory) and
 * {@link JdbcTokenBudgetLedger} (PostgreSQL/H2, atomic conditional UPDATE, daily periods).
 * A Redis or Cycles-service implementation would plug in here.
 */
public interface TokenBudgetAuthority {

    TokenBudgetLedger.Reservation reserve(String tenant, long tokens);

    void commit(TokenBudgetLedger.Reservation reservation, long actualTokens);

    void release(TokenBudgetLedger.Reservation reservation);

    TokenBudgetLedger.Snapshot snapshot(String tenant);
}
