package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlRejectedException;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.AgentStep;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ContextCompactor;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.LoopAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.workflow.WorkflowContext;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The self-healing NL2SQL loop (handbook Part III, Blueprint 7, "SelfHealingSqlLoopAgent"), built on
 * {@link LoopAgent}. Each attempt goes through these stages:
 * <ol>
 *   <li>dialect-aware generation from only the linked tables;</li>
 *   <li>the AST guard;</li>
 *   <li>{@code EXPLAIN} and a cost check (full scans of tables above {@code maxFullScanRows} are refused);</li>
 *   <li>execution in a read-only, rolled-back transaction.</li>
 * </ol>
 * Guard rejections, database errors and empty results go back into the next prompt, together with
 * the plan. There are at most {@code 1 + maxRetries} attempts (the handbook caps retries at 3).
 * Context keys read: {@code tenantId}, {@code question}, {@code linkedSchema}. Written: {@code sql},
 * {@code rows}, {@code attempts}, {@code guardBlocked}, {@code failed}.
 */
public class SelfHealingSqlLoopAgent implements AgentStep {

    public record Attempt(int number, String generatedSql, String executedSql, String outcome, String plan) {}

    static final String SYSTEM = """
            TASK: nl2sql
            You write ONE read-only SELECT for the H2 database (PostgreSQL-compatible syntax, no vendor extensions).
            Rules: use only the tables below; give every table an alias; never write tenant_id (the platform adds
            tenant scoping); no subqueries, CTEs or UNION; reply with SQL only.
            Tables:
            %s""";

    private static final Pattern TABLE_SCAN = Pattern.compile("(?i)PUBLIC\\.(\\w+)\\.tableScan");

    private final ChatClient chat;
    private final SqlAstGuardrailValidator guard;
    private final JdbcClient jdbc;
    private final TransactionTemplate readOnly;
    private final int maxRetries;
    private final long maxFullScanRows;
    private final ContextCompactor compactor;
    private final AtomicInteger executions = new AtomicInteger();

    public SelfHealingSqlLoopAgent(ChatClient chat, SqlAstGuardrailValidator guard, JdbcClient jdbc, TransactionTemplate readOnly,
                                   int maxRetries, long maxFullScanRows, ContextCompactor compactor) {
        this.chat = chat;
        this.guard = guard;
        this.jdbc = jdbc;
        this.readOnly = readOnly;
        this.maxRetries = maxRetries;
        this.maxFullScanRows = maxFullScanRows;
        this.compactor = compactor;
    }

    /** Number of statements sent to JDBC so far (EXPLAIN and query), for tests and metrics. */
    public int jdbcCalls() {
        return executions.get();
    }

    @Override
    public String name() {
        return "self-healing-sql";
    }

    @Override
    public void run(WorkflowContext ctx) {
        ctx.put("attempts", new ArrayList<Attempt>());
        new LoopAgent("heal", this::attempt, c -> c.get("rows").isPresent(), 1 + maxRetries, false, compactor).run(ctx);
        if (ctx.get("rows").isEmpty()) ctx.put("failed", true);
    }

    private void attempt(WorkflowContext ctx) {
        int n = ctx.<Integer>require("heal.iterations");
        String tenant = ctx.require("tenantId");
        String question = ctx.require("question");
        List<Attempt> attempts = ctx.require("attempts");
        String history = ctx.transcript().isEmpty() ? "" : "\n\nPrevious attempts:\n" + String.join("\n", ctx.transcript());
        String generated = clean(chat.prompt().system(SYSTEM.formatted(ctx.<String>require("linkedSchema")))
                .user("Question: " + question + history).call().content());

        SqlAstGuardrailValidator.ValidatedSql v;
        try {
            v = guard.validate(generated, tenant);
        } catch (SqlRejectedException e) {
            ctx.put("guardBlocked", true);
            record(ctx, attempts, new Attempt(n, generated, null, "REJECTED by SQL guard: " + e.getMessage(), null));
            return;
        }
        String plan;
        try {
            executions.incrementAndGet();
            ctx.countToolCall("explain");
            plan = readOnly.execute(s -> {
                s.setRollbackOnly();
                return String.join(" ", jdbc.sql("EXPLAIN " + v.sql()).params(v.params()).query(String.class).list());
            });
            String tooExpensive = costViolation(plan);
            if (tooExpensive != null) {
                record(ctx, attempts, new Attempt(n, generated, v.sql(), "REJECTED by cost check: " + tooExpensive, plan));
                return;
            }
            executions.incrementAndGet();
            ctx.countToolCall("query");
            List<Map<String, Object>> rows = readOnly.execute(s -> {
                s.setRollbackOnly();
                return jdbc.sql(v.sql()).params(v.params()).query().listOfRows();
            });
            if (rows.isEmpty() && n < 1 + maxRetries) {
                record(ctx, attempts, new Attempt(n, generated, v.sql(), "returned 0 rows; check the join conditions against the foreign keys", plan));
                return;
            }
            record(ctx, attempts, new Attempt(n, generated, v.sql(), "OK, " + rows.size() + " rows", plan));
            ctx.put("sql", v.sql());
            ctx.put("rows", rows);
        } catch (DataAccessException e) {
            record(ctx, attempts, new Attempt(n, generated, v.sql(), "DATABASE ERROR: " + rootMessage(e), null));
        }
    }

    private String costViolation(String plan) {
        Matcher m = TABLE_SCAN.matcher(plan);
        while (m.find()) {
            String table = m.group(1);
            Long rows = jdbc.sql("SELECT ROW_COUNT_ESTIMATE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")
                    .param(table.toUpperCase(Locale.ROOT)).query(Long.class).optional().orElse(0L);
            if (rows > maxFullScanRows) return "full scan of " + table + " (~" + rows + " rows, limit " + maxFullScanRows + ")";
        }
        return null;
    }

    private static void record(WorkflowContext ctx, List<Attempt> attempts, Attempt a) {
        attempts.add(a);
        ctx.log("attempt " + a.number() + ": " + a.generatedSql() + "\n  -> " + a.outcome() + (a.plan() == null ? "" : "\n  plan: " + a.plan()));
    }

    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.strip().replaceAll("(?s)^```(?:sql)?\\s*", "").replaceAll("(?s)\\s*```$", "").strip();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).strip();
        return s;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = String.valueOf(t.getMessage());
        return m.lines().findFirst().orElse(m);
    }
}
