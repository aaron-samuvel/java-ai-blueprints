package io.github.aaronsamuvel.blueprints.nl2sql.guard;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Parses model-generated SQL with JSqlParser and only lets through a single read-only SELECT
 * over allow-listed tables, with a row LIMIT of at most {@code maxRows} (added if missing,
 * capped if larger). Anything else throws {@link SqlRejectedException} with a message that is
 * safe to feed back to the model for self-healing.
 */
public class SqlGuard {

    private final long maxRows;
    private final Set<String> allowedTables;

    public SqlGuard(long maxRows, Set<String> allowedTables) {
        this.maxRows = maxRows;
        this.allowedTables = allowedTables.stream().map(t -> t.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public String enforce(String sql) {
        if (sql == null || sql.isBlank()) throw new SqlRejectedException("empty SQL");
        Statements statements;
        try {
            statements = CCJSqlParserUtil.parseStatements(sql);
        } catch (JSQLParserException e) {
            throw new SqlRejectedException("SQL does not parse: " + firstLine(e));
        }
        if (statements.size() != 1) {
            throw new SqlRejectedException("exactly one statement allowed, got " + statements.size());
        }
        Statement st = statements.get(0);
        if (!(st instanceof Select select)) {
            throw new SqlRejectedException("only SELECT is allowed, got " + st.getClass().getSimpleName().toUpperCase(Locale.ROOT));
        }
        if (select instanceof PlainSelect ps && (ps.getIntoTables() != null || ps.getForMode() != null)) {
            throw new SqlRejectedException("SELECT INTO / FOR UPDATE is not allowed");
        }
        Set<String> raw;
        try {
            raw = TablesNamesFinder.findTables(sql);
        } catch (JSQLParserException e) {
            throw new SqlRejectedException("SQL does not parse: " + firstLine(e));
        }
        Set<String> tables = raw.stream()
                .map(t -> t.replace("\"", "").toUpperCase(Locale.ROOT))
                .map(t -> t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t)
                .collect(Collectors.toSet());
        for (String t : tables) {
            if (!allowedTables.contains(t)) throw new SqlRejectedException("table not allowed: " + t);
        }
        Limit limit = select.getLimit();
        if (limit == null || !(limit.getRowCount() instanceof LongValue lv) || lv.getValue() > maxRows) {
            if (limit != null && limit.getRowCount() != null && !(limit.getRowCount() instanceof LongValue)) {
                throw new SqlRejectedException("LIMIT must be a numeric literal");
            }
            Limit capped = new Limit().withRowCount(new LongValue(maxRows));
            if (limit != null && limit.getOffset() != null) capped.setOffset(limit.getOffset());
            select.setLimit(capped);
        }
        return select.toString();
    }

    private static String firstLine(Exception e) {
        String m = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        return m.lines().findFirst().orElse(m);
    }
}
