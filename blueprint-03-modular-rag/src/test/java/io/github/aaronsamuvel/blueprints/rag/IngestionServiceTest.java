package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import io.github.aaronsamuvel.blueprints.rag.ingest.DoclingStubParser;
import io.github.aaronsamuvel.blueprints.rag.ingest.IngestionService;
import io.github.aaronsamuvel.blueprints.rag.ingest.MarkdownSectionParser;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

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

    /** Regression: run from the packaged jar, the corpus pattern also matched directory entries. */
    @Test
    void corpusInsideAJarSkipsDirectoryEntries(@TempDir Path tmp) throws Exception {
        Path jar = tmp.resolve("corpus.jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String dir : List.of("corpus/", "corpus/policy/", "corpus/technical/")) {
                out.putNextEntry(new JarEntry(dir));
                out.closeEntry();
            }
            write(out, "corpus/policy/leave.md", "# Sick leave\n12 days per year\n");
            write(out, "corpus/technical/kafka.md", "# Lag\ncheck consumers\n");
        }
        // The JDK caches open JarFiles for jar: URLs; on Windows that lock stops @TempDir from deleting
        // corpus.jar. Without caching, every jar: connection closes its JarFile when it is done.
        boolean jarCaches = java.net.URLConnection.getDefaultUseCaches("jar");
        java.net.URLConnection.setDefaultUseCaches("jar", false);
        try (URLClassLoader cl = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            IngestionService fromJar = new IngestionService(List.of(new MarkdownSectionParser()),
                    SimpleVectorStore.builder(new StubEmbeddingModel(256)).build());
            assertThat(fromJar.ingestClasspathCorpus(new PathMatchingResourcePatternResolver(cl))).isEqualTo(2);
        } finally {
            java.net.URLConnection.setDefaultUseCaches("jar", jarCaches);
        }
    }

    private static void write(JarOutputStream out, String name, String text) throws Exception {
        out.putNextEntry(new JarEntry(name));
        OutputStream o = out;
        o.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
