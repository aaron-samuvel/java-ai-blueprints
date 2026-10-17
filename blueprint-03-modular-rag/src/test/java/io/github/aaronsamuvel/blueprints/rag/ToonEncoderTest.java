package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.rag.toon.ToonEncoder;

import org.junit.jupiter.api.Test;

class ToonEncoderTest {

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void uniformArrayBecomesTable() {
        String toon = ToonEncoder.encode(row("items", List.of(
                row("id", 1, "name", "Widget", "price", 9.99),
                row("id", 2, "name", "Gadget, large", "price", 19.5))));
        assertThat(toon).isEqualTo("""
                items[2]{id,name,price}:
                  1,Widget,9.99
                  2,"Gadget, large",19.5""");
    }

    @Test
    void primitivesNestedObjectsAndMixedLists() {
        String toon = ToonEncoder.encode(row(
                "user", row("name", "Asha", "active", true),
                "tags", List.of("a", "b"),
                "mixed", List.of(row("x", 1), "plain")));
        assertThat(toon).isEqualTo("""
                user:
                  name: Asha
                  active: true
                tags[2]: a,b
                mixed[2]:
                  -
                    x: 1
                  - plain""");
    }

    @Test
    void ambiguousStringsAreQuoted() {
        assertThat(ToonEncoder.encode(row("a", "true", "b", "42", "c", "k:v"))).isEqualTo("""
                a: "true"
                b: "42"
                c: "k:v\"""");
    }
}
