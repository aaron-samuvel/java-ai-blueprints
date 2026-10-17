package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlRejectedException;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcNamedParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * JSqlParser guard for model-written SQL (handbook Part III, Blueprint 7, "SqlAstGuardrailValidator").
 * It accepts a single plain read-only SELECT over allow-listed tables and rejects:
 * <ul>
 *   <li>DML, DDL and multiple statements;</li>
 *   <li>UNION and other set operations, CTEs, subqueries, and SELECT INTO / FOR UPDATE;</li>
 *   <li>any mention of {@code tenant_id}.</li>
 * </ul>
 * It then adds {@code <alias>.tenant_id = :tenantId} for every table in FROM and JOIN, and adds
 * or caps the LIMIT at {@code maxRows}. The rejection messages are safe to feed back to the model.
 */
public class SqlAstGuardrailValidator {

    public record ValidatedSql(String sql, Map<String, Object> params, List<String> tables) {}

    private static final Pattern TENANT_REF = Pattern.compile("(?i)\\btenant_id\\b");
    private static final Pattern TENANT_FORMAT = Pattern.compile("[a-z][a-z0-9-]{1,19}");

    private final Set<String> allowedTables;
    private final long maxRows;

    public SqlAstGuardrailValidator(Set<String> allowedTables, long maxRows) {
        this.allowedTables = allowedTables.stream().map(t -> t.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        this.maxRows = maxRows;
    }

    public ValidatedSql validate(String sql, String tenantId) {
        if (tenantId == null || !TENANT_FORMAT.matcher(tenantId).matches()) throw new SqlRejectedException("invalid tenant id");
        if (sql == null || sql.isBlank()) throw new SqlRejectedException("empty SQL");
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            throw new SqlRejectedException("SQL does not parse: " + firstLine(e));
        }
        if (statements.size() != 1) throw new SqlRejectedException("exactly one statement allowed, got " + statements.size());
        Statement st = statements.get(0);
        if (!(st instanceof PlainSelect ps)) {
            throw new SqlRejectedException("only a plain read-only SELECT is allowed, got " + st.getClass().getSimpleName().toUpperCase(Locale.ROOT));
        }
        if (ps.getWithItemsList() != null && !ps.getWithItemsList().isEmpty()) throw new SqlRejectedException("WITH (CTE) is not allowed");
        if (ps.getIntoTables() != null || ps.getForMode() != null) throw new SqlRejectedException("SELECT INTO / FOR UPDATE is not allowed");
        if (TENANT_REF.matcher(sql).find()) {
            throw new SqlRejectedException("do not reference tenant_id; tenant scoping is added automatically");
        }

        List<Table> fromTables = new ArrayList<>();
        addTable(ps.getFromItem(), fromTables);
        if (ps.getJoins() != null) for (Join j : ps.getJoins()) addTable(j.getRightItem(), fromTables);

        Set<String> all;
        try {
            all = TablesNamesFinder.findTables(sql).stream().map(SqlAstGuardrailValidator::bare).collect(Collectors.toSet());
        } catch (JSQLParserException e) {
            throw new SqlRejectedException("SQL does not parse: " + firstLine(e));
        }
        Set<String> direct = fromTables.stream().map(t -> bare(t.getName())).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!direct.containsAll(all)) throw new SqlRejectedException("subqueries are not allowed");
        for (String t : direct) if (!allowedTables.contains(t)) throw new SqlRejectedException("table not allowed: " + t);

        Expression scope = null;
        for (Table t : fromTables) {
            String ref = t.getAlias() != null ? t.getAlias().getName() : t.getName();
            EqualsTo eq = new EqualsTo(new Column(new Table(ref), "tenant_id"), new JdbcNamedParameter("tenantId"));
            scope = scope == null ? eq : new AndExpression(scope, eq);
        }
        ps.setWhere(ps.getWhere() == null ? scope : new AndExpression(new ParenthesedExpressionList<>(ps.getWhere()), scope));

        Limit limit = ps.getLimit();
        if (limit != null && limit.getRowCount() != null && !(limit.getRowCount() instanceof LongValue)) {
            throw new SqlRejectedException("LIMIT must be a numeric literal");
        }
        if (limit == null || limit.getRowCount() == null || ((LongValue) limit.getRowCount()).getValue() > maxRows) {
            Limit capped = new Limit().withRowCount(new LongValue(maxRows));
            if (limit != null && limit.getOffset() != null) capped.setOffset(limit.getOffset());
            ps.setLimit(capped);
        }
        return new ValidatedSql(ps.toString(), Map.of("tenantId", tenantId), List.copyOf(direct));
    }

    private static void addTable(FromItem item, List<Table> out) {
        if (item == null) throw new SqlRejectedException("FROM clause is required");
        if (!(item instanceof Table t)) throw new SqlRejectedException("only plain tables are allowed in FROM/JOIN (no subqueries)");
        out.add(t);
    }

    private static String bare(String name) {
        String n = name.replace("\"", "").toUpperCase(Locale.ROOT);
        return n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : n;
    }

    private static String firstLine(Exception e) {
        String m = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        return m.lines().findFirst().orElse(m);
    }
}
