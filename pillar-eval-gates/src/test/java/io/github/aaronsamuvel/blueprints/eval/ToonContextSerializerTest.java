package io.github.aaronsamuvel.blueprints.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cedarsoftware.io.JsonIo;
import io.github.aaronsamuvel.blueprints.eval.toon.ToonContextSerializer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToonContextSerializerTest {

    record Line(String sku, int qty, double price) {}

    record Order(String id, String customer, String status, List<Line> lines) {}

    static List<Order> orders(int n) {
        List<Order> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new Order("ORD-" + (1000 + i), "tenant-acme-" + (i % 7), i % 3 == 0 ? "SHIPPED" : "PENDING",
                    List.of(new Line("SKU-" + (i % 11), 1 + i % 4, 9.5 + i), new Line("SKU-" + (i % 5), 2, 20.0))));
        }
        return out;
    }

    record Row(String id, String tenant, String status, int qty, double amount) {}

    static List<Row> rows(int n) {
        List<Row> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Row("ORD-" + (1000 + i), "acme-" + (i % 7), i % 3 == 0 ? "SHIPPED" : "PENDING", 1 + i % 4, 9.5 + i));
        return out;
    }

    @Test
    void measureFlatAndNestedShapesHonestly() {
        var flat = ToonContextSerializer.compare(rows(40));
        var nested = ToonContextSerializer.compare(orders(40));
        int nestedPretty = io.github.aaronsamuvel.blueprints.eval.gates.AnswerTokenBudgetEvaluator.count(
                tools.jackson.databind.json.JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(orders(40)));
        System.out.printf("TOON flat 40 rows: json=%d toon=%d reduction=%.1f%%%n", flat.jsonTokens(), flat.toonTokens(), flat.reductionPercent());
        System.out.printf("TOON nested 40 orders: compact json=%d pretty json=%d toon=%d reduction vs compact=%.1f%%%n",
                nested.jsonTokens(), nestedPretty, nested.toonTokens(), nested.reductionPercent());
        // Tabular (uniform, flat) arrays are where TOON saves tokens; the gate asserts only that.
        assertThat(flat.toonTokens()).isLessThan(flat.jsonTokens());
        assertThat(nested.toonTokens()).isLessThan(nestedPretty);
    }

    @Test
    void toonRoundTripsBackToTheSameFields() {
        String toon = ToonContextSerializer.toToon(orders(3));
        assertThat(toon).contains("lines[2]{sku,qty,price}:");
        Object[] back = JsonIo.fromToonToMaps(toon).asClass(Object[].class);
        assertThat(back).hasSize(3);
        Map<?, ?> first = (Map<?, ?>) back[0];
        assertThat(first.get("id")).isEqualTo("ORD-1000");
        assertThat(first.get("status")).isEqualTo("SHIPPED");
    }
}
