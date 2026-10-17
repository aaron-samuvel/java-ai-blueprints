package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import io.github.aaronsamuvel.blueprints.rag.ingest.DoclingStubParser;
import io.github.aaronsamuvel.blueprints.rag.ingest.IngestionService;
import io.github.aaronsamuvel.blueprints.rag.ingest.MarkdownSectionParser;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;

class IngestionServiceTest {

    private final IngestionService ingestion = new IngestionService(
            List.of(new MarkdownSectionParser(), new DoclingStubParser()),
            SimpleVectorStore.builder(new StubEmbeddingModel(256)).build());

    @Test
    void markdownIsSplitPerSectionWithMetadata() {
        List<Document> docs = ingestion.toDocuments("policy", "p.md", "# A\nalpha text\n# B\nbeta text\n");
        assertThat(docs).hasSize(2);
        assertThat(docs.get(1).getMetadata())
                .containsEntry("section", "B").containsEntry("source", "p.md").containsEntry("domain", "policy");
    }

    @Test
    void pdfGoesThroughDoclingStub() {
        List<Document> docs = ingestion.toDocuments("technical", "manual.pdf", "# Intro\nconverted text");
        assertThat(docs).singleElement().satisfies(d -> assertThat(d.getMetadata()).containsEntry("parser", "docling-stub"));
    }

    @Test
    void unsupportedTypeFailsLoudly() {
        assertThatThrownBy(() -> ingestion.toDocuments("x", "image.png", "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void classpathCorpusIsIngested() {
        assertThat(ingestion.ingestClasspathCorpus()).isEqualTo(8); // 4 files x 2 sections
    }
}
