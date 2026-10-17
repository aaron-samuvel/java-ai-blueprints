package io.github.aaronsamuvel.blueprints.rag.toon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal encoder for TOON (Token-Oriented Object Notation), covering the subset that matters
 * for RAG context: objects, primitive arrays, and <i>uniform arrays of flat objects</i>, which
 * become a header plus CSV-like rows:
 * <pre>
 * items[2]{id,name,price}:
 *   1,Widget,9.99
 *   2,Gadget,19.5
 * </pre>
 * Non-uniform arrays fall back to a YAML-like "- " list. Strings containing a delimiter,
 * a colon, quotes or surrounding spaces are quoted. This is <b>author's design</b> following the
 * public TOON idea; it is not a complete, certified implementation of the spec.
 */
public final class ToonEncoder {

    private ToonEncoder() {}

    public static String encode(Object value) {
        StringBuilder sb = new StringBuilder();
        if (value instanceof Map<?, ?> m) {
            writeObject(sb, m, 0);
        } else {
            writeField(sb, "items", value, 0);
        }
        return sb.toString().stripTrailing();
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m, int indent) {
        m.forEach((k, v) -> writeField(sb, String.valueOf(k), v, indent));
    }

    private static void writeField(StringBuilder sb, String key, Object v, int indent) {
        String pad = "  ".repeat(indent);
        if (v instanceof Map<?, ?> m) {
            sb.append(pad).append(key).append(":\n");
            writeObject(sb, m, indent + 1);
        } else if (v instanceof List<?> list) {
            List<String> header = uniformHeader(list);
            if (header != null) {
                sb.append(pad).append(key).append('[').append(list.size()).append("]{")
                        .append(String.join(",", header)).append("}:\n");
                for (Object row : list) {
                    Map<?, ?> r = (Map<?, ?>) row;
                    List<String> cells = new ArrayList<>();
                    for (String h : header) cells.add(scalar(r.get(h)));
                    sb.append(pad).append("  ").append(String.join(",", cells)).append('\n');
                }
            } else if (list.stream().allMatch(ToonEncoder::isScalar)) {
                List<String> cells = list.stream().map(ToonEncoder::scalar).toList();
                sb.append(pad).append(key).append('[').append(list.size()).append("]: ")
                        .append(String.join(",", cells)).append('\n');
            } else {
                sb.append(pad).append(key).append('[').append(list.size()).append("]:\n");
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        sb.append(pad).append("  -\n");
                        writeObject(sb, m, indent + 2);
                    } else {
                        sb.append(pad).append("  - ").append(scalar(item)).append('\n');
                    }
                }
            }
        } else {
            sb.append(pad).append(key).append(": ").append(scalar(v)).append('\n');
        }
    }

    /** Header if every element is a Map with the same keys and only scalar values; else null. */
    static List<String> uniformHeader(List<?> list) {
        if (list.isEmpty() || !(list.get(0) instanceof Map<?, ?> first)) return null;
        Set<String> keys = new LinkedHashSet<>();
        first.keySet().forEach(k -> keys.add(String.valueOf(k)));
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m) || m.size() != keys.size()) return null;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!keys.contains(String.valueOf(e.getKey())) || !isScalar(e.getValue())) return null;
            }
        }
        return List.copyOf(keys);
    }

    static boolean isScalar(Object o) {
        return o == null || o instanceof String || o instanceof Number || o instanceof Boolean;
    }

    static String scalar(Object o) {
        if (o == null) return "null";
        if (o instanceof Number || o instanceof Boolean) return o.toString();
        String s = o.toString();
        boolean quote = s.isEmpty() || s.contains(",") || s.contains(":") || s.contains("\"")
                || s.contains("\n") || !s.equals(s.strip()) || s.equals("true") || s.equals("false")
                || s.equals("null") || s.matches("-?\\d+(\\.\\d+)?");
        return quote ? "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"" : s;
    }
}
