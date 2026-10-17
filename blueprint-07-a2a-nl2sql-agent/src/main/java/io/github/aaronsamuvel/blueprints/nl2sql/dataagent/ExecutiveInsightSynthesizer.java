package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a result set into a {@link DataAnalysisReport} (handbook Part III, Blueprint 7,
 * "ExecutiveInsightSynthesizer"):
 * <ul>
 *   <li>a time-like column with a numeric one gives a LINE chart;</li>
 *   <li>a text column with a numeric one gives a BAR chart;</li>
 *   <li>anything else gives a TABLE.</li>
 * </ul>
 * The summary is computed from the rows, not written by a model, so it cannot state numbers that
 * are not in the data.
 */
public class ExecutiveInsightSynthesizer {

    public DataAnalysisReport synthesize(String question, String sql, List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return new DataAnalysisReport("No rows matched \"" + question + "\".", sql, rows,
                    new ChartSpecification(ChartSpecification.Type.TABLE, null, null, question));
        }
        Map<String, Object> first = rows.get(0);
        Optional<String> numeric = first.keySet().stream().filter(k -> first.get(k) instanceof Number).reduce((a, b) -> b);
        Optional<String> time = first.keySet().stream().filter(k -> isTime(k, first.get(k))).findFirst();
        Optional<String> label = first.keySet().stream().filter(k -> first.get(k) instanceof String && time.map(t -> !t.equals(k)).orElse(true)).findFirst();

        ChartSpecification chart;
        if (numeric.isPresent() && time.isPresent()) {
            chart = new ChartSpecification(ChartSpecification.Type.LINE, time.get(), numeric.get(), question);
        } else if (numeric.isPresent() && label.isPresent()) {
            chart = new ChartSpecification(ChartSpecification.Type.BAR, label.get(), numeric.get(), question);
        } else {
            chart = new ChartSpecification(ChartSpecification.Type.TABLE, null, null, question);
        }
        StringBuilder s = new StringBuilder(rows.size() + (rows.size() == 1 ? " row" : " rows") + " for \"" + question + "\".");
        if (numeric.isPresent() && (label.isPresent() || time.isPresent())) {
            String y = numeric.get();
            String x = time.orElseGet(label::get);
            Comparator<Map<String, Object>> byY = Comparator.comparing(r -> num(r.get(y)));
            Map<String, Object> max = rows.stream().max(byY).orElseThrow();
            Map<String, Object> min = rows.stream().min(byY).orElseThrow();
            BigDecimal total = rows.stream().map(r -> num(r.get(y))).reduce(BigDecimal.ZERO, BigDecimal::add);
            s.append(" Highest ").append(y.toLowerCase(Locale.ROOT)).append(": ").append(max.get(x)).append(" (").append(plain(max.get(y))).append(").");
            if (rows.size() > 1) s.append(" Lowest: ").append(min.get(x)).append(" (").append(plain(min.get(y))).append(").");
            s.append(" Total: ").append(total.stripTrailingZeros().toPlainString()).append('.');
        }
        return new DataAnalysisReport(s.toString(), sql, rows, chart);
    }

    private static boolean isTime(String key, Object v) {
        if (v instanceof Temporal || v instanceof java.util.Date) return true;
        String k = key.toLowerCase(Locale.ROOT);
        return v instanceof String str && (k.contains("month") || k.contains("date") || k.contains("day")) && str.matches("\\d{4}-\\d{2}.*");
    }

    private static BigDecimal num(Object o) {
        return o instanceof BigDecimal b ? b : o instanceof Number n ? new BigDecimal(n.toString()) : BigDecimal.ZERO;
    }

    private static String plain(Object o) {
        return num(o).stripTrailingZeros().toPlainString();
    }
}
