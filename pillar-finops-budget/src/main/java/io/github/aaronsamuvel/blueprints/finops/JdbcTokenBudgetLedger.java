package io.github.aaronsamuvel.blueprints.finops;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Database-backed budget authority (handbook Part IV, 10.1: "atomically reserve that quota in
 * Redis/PostgreSQL"). Budgets are per tenant and per day (UTC by default).
 * <p>The reservation is a single conditional statement:
 * {@code UPDATE finops_budget SET reserved = reserved + ? WHERE ... AND used + reserved + ? <= limit_tokens}.
 * The database row lock makes concurrent reservations serialise, so the budget cannot be
 * oversubscribed even with many application instances. Open reservations are rows in
 * {@code finops_reservation}, so a crash leaves them visible for a sweeper. Schema: {@link #DDL}.
 */
public class JdbcTokenBudgetLedger implements TokenBudgetAuthority {

    public static final String DDL = """
            CREATE TABLE IF NOT EXISTS finops_limit (tenant_id VARCHAR(64) PRIMARY KEY, daily_limit BIGINT NOT NULL);
            CREATE TABLE IF NOT EXISTS finops_budget (tenant_id VARCHAR(64) NOT NULL, period VARCHAR(10) NOT NULL,
              limit_tokens BIGINT NOT NULL, reserved BIGINT NOT NULL, used BIGINT NOT NULL, PRIMARY KEY (tenant_id, period));
            CREATE TABLE IF NOT EXISTS finops_reservation (id VARCHAR(36) PRIMARY KEY, tenant_id VARCHAR(64) NOT NULL,
              period VARCHAR(10) NOT NULL, tokens BIGINT NOT NULL);
            """;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final long defaultDailyLimit;
    private final Clock clock;

    public JdbcTokenBudgetLedger(DataSource dataSource, long defaultDailyLimit, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.defaultDailyLimit = defaultDailyLimit;
        this.clock = clock;
    }

    public JdbcTokenBudgetLedger createSchema() {
        for (String stmt : DDL.split(";")) if (!stmt.isBlank()) jdbc.execute(stmt);
        return this;
    }

    public void setDailyLimit(String tenant, long limit) {
        tx.executeWithoutResult(s -> {
            if (jdbc.update("UPDATE finops_limit SET daily_limit = ? WHERE tenant_id = ?", limit, tenant) == 0) {
                jdbc.update("INSERT INTO finops_limit (tenant_id, daily_limit) VALUES (?, ?)", tenant, limit);
            }
            jdbc.update("UPDATE finops_budget SET limit_tokens = ? WHERE tenant_id = ? AND period = ?", limit, tenant, period());
        });
    }

    private String period() {
        return LocalDate.now(clock).toString();
    }

    private void ensureRow(String tenant, String period) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM finops_budget WHERE tenant_id = ? AND period = ?", Integer.class, tenant, period);
        if (exists != null && exists > 0) return;
        List<Long> limit = jdbc.queryForList("SELECT daily_limit FROM finops_limit WHERE tenant_id = ?", Long.class, tenant);
        try {
            jdbc.update("INSERT INTO finops_budget (tenant_id, period, limit_tokens, reserved, used) VALUES (?, ?, ?, 0, 0)",
                    tenant, period, limit.isEmpty() ? defaultDailyLimit : limit.get(0));
        } catch (org.springframework.dao.DuplicateKeyException raced) {
            // another instance created it first
        }
    }

    @Override
    public TokenBudgetLedger.Reservation reserve(String tenant, long tokens) {
        if (tokens <= 0) throw new IllegalArgumentException("tokens must be positive");
        String period = period();
        ensureRow(tenant, period);
        return tx.execute(s -> {
            int updated = jdbc.update("UPDATE finops_budget SET reserved = reserved + ? WHERE tenant_id = ? AND period = ? "
                    + "AND used + reserved + ? <= limit_tokens", tokens, tenant, period, tokens);
            if (updated == 0) {
                TokenBudgetLedger.Snapshot snap = snapshot(tenant, period);
                throw new BudgetExceededException(tenant, tokens, snap.available());
            }
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO finops_reservation (id, tenant_id, period, tokens) VALUES (?, ?, ?, ?)", id, tenant, period, tokens);
            return new TokenBudgetLedger.Reservation(id, tenant, tokens);
        });
    }

    @Override
    public void commit(TokenBudgetLedger.Reservation r, long actualTokens) {
        settle(r, Math.max(0, actualTokens), true);
    }

    @Override
    public void release(TokenBudgetLedger.Reservation r) {
        settle(r, 0, false);
    }

    private void settle(TokenBudgetLedger.Reservation r, long actual, boolean mustExist) {
        tx.executeWithoutResult(s -> {
            List<String> period = jdbc.queryForList("SELECT period FROM finops_reservation WHERE id = ?", String.class, r.id());
            if (period.isEmpty() || jdbc.update("DELETE FROM finops_reservation WHERE id = ?", r.id()) == 0) {
                if (mustExist) throw new IllegalStateException("reservation already settled: " + r.id());
                return;
            }
            jdbc.update("UPDATE finops_budget SET reserved = reserved - ?, used = used + ? WHERE tenant_id = ? AND period = ?",
                    r.tokens(), actual, r.tenant(), period.get(0));
        });
    }

    @Override
    public TokenBudgetLedger.Snapshot snapshot(String tenant) {
        String period = period();
        ensureRow(tenant, period);
        return snapshot(tenant, period);
    }

    private TokenBudgetLedger.Snapshot snapshot(String tenant, String period) {
        return jdbc.queryForObject("SELECT limit_tokens, reserved, used FROM finops_budget WHERE tenant_id = ? AND period = ?",
                (rs, i) -> new TokenBudgetLedger.Snapshot(tenant, rs.getLong(1), rs.getLong(2), rs.getLong(3)), tenant, period);
    }
}
