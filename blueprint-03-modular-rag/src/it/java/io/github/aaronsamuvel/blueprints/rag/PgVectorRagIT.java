package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.aaronsamuvel.blueprints.rag.ingest.IngestionService;
import io.github.aaronsamuvel.blueprints.rag.ingest.MarkdownSectionParser;
import io.github.aaronsamuvel.blueprints.rag.routing.KeywordQueryRouter;
import io.github.aaronsamuvel.blueprints.rag.routing.QueryIntent;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Live: Postgres with the pgvector extension + Ollama nomic-embed-text (768 dims).
 * Connection comes ONLY from env vars: PGHOST (default localhost), PGPORT (default 15432),
 * PGDATABASE (default postgres), PGUSER (default dev), PGPASSWORD (required, never in code).
 * Run: mvn -B -Pit verify -pl blueprint-03-modular-rag -am
 */
class PgVectorRagIT {

    private static String env(String k, String d) {
        String v = System.getenv(k);
        return v == null || v.isBlank() ? d : v;
    }

    @Test
    void retrievesFromPgvectorWithRealEmbeddings() {
        String password = System.getenv("PGPASSWORD");
        assertThat(password).as("set PGPASSWORD in your shell (not in a file)").isNotBlank();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:postgresql://" + env("PGHOST", "localhost") + ":" + env("PGPORT", "15432") + "/" + env("PGDATABASE", "postgres"),
                env("PGUSER", "dev"), password);
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        OllamaEmbeddingModel embeddings = OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(env("OLLAMA_BASE_URL", "http://localhost:11434")).build())
                .options(OllamaEmbeddingOptions.builder().model(env("OLLAMA_EMBED_MODEL", "nomic-embed-text")).build())
                .build();

        PgVectorStore store = PgVectorStore.builder(jdbc, embeddings)
                .vectorTableName("blueprint03_it_chunks")
                .dimensions(768)
                .initializeSchema(true)
                .removeExistingVectorStoreTable(true)
                .build();
        store.afterPropertiesSet();

        new IngestionService(List.of(new MarkdownSectionParser()), store).ingestClasspathCorpus();

        StubChatModel model = StubChatModel.echo();
        RagAnswer a = new ModularRagService(model, store, new KeywordQueryRouter(), 3, 0.3)
                .ask("How many days do I have to request a refund?");

        assertThat(a.intent()).isEqualTo(QueryIntent.POLICY);
        assertThat(a.sources()).anyMatch(s -> s.startsWith("refund-policy.md"));
        assertThat(model.allSentText()).contains("30 days");
    }
}
