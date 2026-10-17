package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.knuddels.jtokkit.api.EncodingType;
import io.github.aaronsamuvel.blueprints.rag.toon.TokenCounter;
import io.github.aaronsamuvel.blueprints.rag.toon.ToonEncoder;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Measures real BPE token counts for the same data as pretty JSON, compact JSON and TOON.
 * Data is generated with a fixed seed, so numbers are reproducible. Results are written to
 * target/toon-measurement.md and printed. The handbook's "40-50% saving" is checked, not assumed.
 */
class ToonVsJsonTokenMeasurementTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] CATS = {"electronics", "books", "kitchen", "garden", "toys"};
    private static final String[] NAMES = {"Widget", "Gadget", "Kettle", "Lamp", "Notebook", "Drill", "Puzzle", "Mug"};

    record Result(String dataset, String tokenizer, int pretty, int compact, int toon) {
        double vsPretty() { return 100.0 * (pretty - toon) / pretty; }
        double vsCompact() { return 100.0 * (compact - toon) / compact; }
    }

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    static Map<String, Object> uniformProducts() {
        Random r = new Random(42);
        List<Object> rows = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            rows.add(m("id", i, "sku", "SKU-" + (10000 + i), "name", NAMES[r.nextInt(NAMES.length)] + " " + i,
                    "category", CATS[r.nextInt(CATS.length)], "price", Math.round(r.nextDouble() * 500_00) / 100.0,
                    "inStock", r.nextBoolean()));
        }
        return m("products", rows);
    }

    static Map<String, Object> ragChunks() {
        Random r = new Random(7);
        List<Object> rows = new ArrayList<>();
        String[] sources = {"refund-policy.md", "leave-policy.md", "kafka-runbook.md", "spring-boot-troubleshooting.md"};
        for (int i = 0; i < 20; i++) {
            rows.add(m("source", sources[r.nextInt(4)], "section", "Section " + (i + 1),
                    "score", Math.round(r.nextDouble() * 1000) / 1000.0,
                    "text", "Customers can request a full refund within 30 days of delivery and refunds take 7 working days " + i));
        }
        return m("context", rows);
    }

    static Map<String, Object> nestedOrders() {
        Random r = new Random(99);
        List<Object> orders = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            List<Object> lines = new ArrayList<>();
            for (int j = 0; j < 1 + r.nextInt(3); j++) {
                Map<String, Object> line = m("sku", "SKU-" + r.nextInt(99999), "qty", 1 + r.nextInt(4));
                if (r.nextBoolean()) line.put("discount", m("code", "FEST" + j, "pct", 5 + r.nextInt(20)));
                lines.add(line);
            }
            orders.add(m("orderId", "ORD-" + i, "customer", m("id", "C-" + (1000 + i), "tier", r.nextBoolean() ? "gold" : "silver"),
                    "lines", lines, "notes", r.nextBoolean() ? "leave at door" : null));
        }
        return m("orders", orders);
    }

    @Test
    void measureAndReport() throws Exception {
        List<TokenCounter> counters = List.of(new TokenCounter(EncodingType.CL100K_BASE), new TokenCounter(EncodingType.O200K_BASE));
        Map<String, Map<String, Object>> datasets = new LinkedHashMap<>();
        datasets.put("100 uniform product rows (6 fields)", uniformProducts());
        datasets.put("20 RAG chunks with metadata (4 fields, long text)", ragChunks());
        datasets.put("20 nested, non-uniform orders", nestedOrders());

        List<Result> results = new ArrayList<>();
        datasets.forEach((name, data) -> {
            String pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            String compact = JSON.writeValueAsString(data);
            String toon = ToonEncoder.encode(data);
            for (TokenCounter c : counters) {
                results.add(new Result(name, c.name(), c.count(pretty), c.count(compact), c.count(toon)));
            }
        });

        StringBuilder md = new StringBuilder("| Dataset | Tokenizer | JSON pretty | JSON compact | TOON | TOON vs pretty | TOON vs compact |\n|---|---|---|---|---|---|---|\n");
        for (Result x : results) {
            md.append(String.format("| %s | %s | %d | %d | %d | %.1f%% | %.1f%% |%n",
                    x.dataset(), x.tokenizer(), x.pretty(), x.compact(), x.toon(), x.vsPretty(), x.vsCompact()));
        }
        Path out = Path.of("target", "toon-measurement.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md.toString());
        System.out.println("\n=== TOON vs JSON token measurement ===\n" + md);

        // Uniform tabular data is where TOON is designed to win: it must beat compact JSON there.
        results.stream().filter(x -> x.dataset().startsWith("100 uniform"))
                .forEach(x -> assertThat(x.toon()).isLessThan(x.compact()));
        // Every encoding must be non-empty; no other claim is asserted, the numbers are reported as measured.
        assertThat(results).allMatch(x -> x.toon() > 0 && x.compact() > 0);
    }
}
