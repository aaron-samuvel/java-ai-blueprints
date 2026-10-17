package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.util.List;
import java.util.Map;

/** One table as the indexer saw it in {@code DatabaseMetaData} (tenant_id is left out on purpose). */
public record TableDescriptor(String name, String remarks, List<String> columns, List<String> foreignKeys,
                              Map<String, List<String>> enumSamples, List<String> referencedTables) {

    /** Compact DDL-like text injected into the prompt and embedded into the vector store. */
    public String render() {
        StringBuilder sb = new StringBuilder("TABLE ").append(name).append(" -- ").append(remarks).append("\n  columns: ")
                .append(String.join(", ", columns));
        if (!foreignKeys.isEmpty()) sb.append("\n  foreign keys: ").append(String.join(", ", foreignKeys));
        enumSamples.forEach((col, values) -> sb.append("\n  ").append(col).append(" values: ").append(String.join(", ", values)));
        return sb.toString();
    }
}
