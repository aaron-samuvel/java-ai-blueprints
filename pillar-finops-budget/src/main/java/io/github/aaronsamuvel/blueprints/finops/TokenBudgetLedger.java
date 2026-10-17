package io.github.aaronsamuvel.blueprints.finops;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-tenant token ledger implementing reserve, call, commit:
 * <ol>
 *   <li><b>reserve</b> the worst-case estimate <i>before</i> calling the model. This fails fast if
 *       {@code used + reserved + estimate > limit}, so concurrent calls can never overspend;</li>
 *   <li><b>commit</b> the actual usage afterwards, releasing the reservation;</li>
 *   <li><b>release</b> the reservation if the call failed.</li>
 * </ol>
 * In-memory and thread-safe (one lock per tenant). Production would back it with Postgres or Redis.
 */
public class TokenBudgetLedger {

    public record Reservation(String id, String tenant, long tokens) {}

    public record Snapshot(String tenant, long limit, long reserved, long used) {
        public long available() { return Math.max(0, limit - reserved - used); }
    }

    private static final class Account {
        long limit, reserved, used;
        final Map<String, Long> open = new ConcurrentHashMap<>();
        Account(long limit) { this.limit = limit; }
    }

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final long defaultLimit;

    public TokenBudgetLedger(long defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    public void setLimit(String tenant, long limit) {
        Account a = account(tenant);
        synchronized (a) { a.limit = limit; }
    }

    public Reservation reserve(String tenant, long tokens) {
        if (tokens <= 0) throw new IllegalArgumentException("tokens must be positive");
        Account a = account(tenant);
        synchronized (a) {
            long available = a.limit - a.reserved - a.used;
            if (tokens > available) throw new BudgetExceededException(tenant, tokens, Math.max(0, available));
            a.reserved += tokens;
            Reservation r = new Reservation(UUID.randomUUID().toString(), tenant, tokens);
            a.open.put(r.id(), tokens);
            return r;
        }
    }

    /** Record actual usage. Actual may exceed the estimate; it is charged anyway (it was spent). */
    public void commit(Reservation r, long actualTokens) {
        Account a = account(r.tenant());
        synchronized (a) {
            Long held = a.open.remove(r.id());
            if (held == null) throw new IllegalStateException("reservation already settled: " + r.id());
            a.reserved -= held;
            a.used += Math.max(0, actualTokens);
        }
    }

    public void release(Reservation r) {
        Account a = account(r.tenant());
        synchronized (a) {
            Long held = a.open.remove(r.id());
            if (held != null) a.reserved -= held;
        }
    }

    public Snapshot snapshot(String tenant) {
        Account a = account(tenant);
        synchronized (a) { return new Snapshot(tenant, a.limit, a.reserved, a.used); }
    }

    private Account account(String tenant) {
        return accounts.computeIfAbsent(tenant, t -> new Account(defaultLimit));
    }
}
