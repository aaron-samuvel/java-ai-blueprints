package io.github.aaronsamuvel.blueprints.nl2sql.agent;

import java.util.List;
import java.util.Map;

/**
 * Outcome of one NL2SQL request. {@code attempts} counts model calls (1 = first try worked).
 * {@code errors} holds the guard or database error of every failed attempt, in order.
 */
public record Nl2SqlResult(boolean success, String sql, List<Map<String, Object>> rows, int attempts, List<String> errors) {
    public Nl2SqlResult {
        rows = rows == null ? List.of() : List.copyOf(rows);
        errors = List.copyOf(errors);
    }
}
