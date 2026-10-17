package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Introspects JDBC {@link DatabaseMetaData} (tables with their remarks, columns, foreign keys) plus
 * samples of low-cardinality text columns, and indexes one document per table into a vector store.
 * {@link #relevantTables} returns the top-K tables for a question plus the tables they reference
 * by foreign key, so join paths stay available. Only tables matching {@code tablePrefix} are indexed.
 * <p>Samples are taken across all tenants. Keep {@code maxEnumValues} small, and exclude columns
 * whose values identify a tenant.
 */
public class SchemaVectorIndexer {

    private final DataSource dataSource;
    private final JdbcClient jdbc;
    private final VectorStore store;
    private final String tablePrefix;
    private final int maxEnumValues;
    private final Map<String, TableDescriptor> tables = new LinkedHashMap<>();

    public SchemaVectorIndexer(DataSource dataSource, VectorStore store, String tablePrefix, int maxEnumValues) {
        this.dataSource = dataSource;
        this.jdbc = JdbcClient.create(dataSource);
        this.store = store;
        this.tablePrefix = tablePrefix.toUpperCase(Locale.ROOT);
        this.maxEnumValues = maxEnumValues;
    }

    public synchronized List<TableDescriptor> index() {
        tables.clear();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(null, null, tablePrefix + "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String schema = rs.getString("TABLE_SCHEM");
                    tables.put(name, describe(md, schema, name, String.valueOf(rs.getString("REMARKS"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("schema introspection failed", e);
        }
        store.add(tables.values().stream().map(t -> new Document("schema-" + t.name(), t.render(), Map.of("table", t.name()))).toList());
        return List.copyOf(tables.values());
    }

    private TableDescriptor describe(DatabaseMetaData md, String schema, String table, String remarks) throws SQLException {
        List<String> columns = new ArrayList<>();
        List<String> textColumns = new ArrayList<>();
        try (ResultSet rs = md.getColumns(null, schema, table, null)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col.equalsIgnoreCase("TENANT_ID")) continue;
                String type = rs.getString("TYPE_NAME");
                columns.add(col + " " + type);
                if (type.toUpperCase(Locale.ROOT).contains("CHAR") && !col.equalsIgnoreCase("NAME")) textColumns.add(col);
            }
        }
        List<String> fks = new ArrayList<>();
        Set<String> referenced = new LinkedHashSet<>();
        try (ResultSet rs = md.getImportedKeys(null, schema, table)) {
            while (rs.next()) {
                String fkCol = rs.getString("FKCOLUMN_NAME");
                if (fkCol.equalsIgnoreCase("TENANT_ID")) continue;
                fks.add(fkCol + " -> " + rs.getString("PKTABLE_NAME") + "." + rs.getString("PKCOLUMN_NAME"));
                referenced.add(rs.getString("PKTABLE_NAME"));
            }
        }
        Map<String, List<String>> samples = new LinkedHashMap<>();
        for (String col : textColumns) {
            List<String> values = jdbc.sql("SELECT DISTINCT " + col + " FROM " + table + " ORDER BY 1 LIMIT " + (maxEnumValues + 1))
                    .query(String.class).list();
            if (values.size() <= maxEnumValues) samples.put(col, values);
        }
        return new TableDescriptor(table, remarks, columns, fks, samples, List.copyOf(referenced));
    }

    public synchronized List<TableDescriptor> relevantTables(String question, int topK) {
        List<Document> hits = store.similaritySearch(SearchRequest.builder().query(question).topK(topK).similarityThresholdAll().build());
        Set<String> names = new LinkedHashSet<>();
        for (Document d : hits) names.add(String.valueOf(d.getMetadata().get("table")));
        for (String n : List.copyOf(names)) {
            TableDescriptor t = tables.get(n);
            if (t != null) names.addAll(t.referencedTables());
        }
        return names.stream().map(tables::get).filter(java.util.Objects::nonNull).toList();
    }

    public synchronized Set<String> indexedTables() {
        return Set.copyOf(tables.keySet());
    }
}
