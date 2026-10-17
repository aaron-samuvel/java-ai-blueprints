package io.github.aaronsamuvel.blueprints.rag.ingest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * RAG ETL: extract (parser chosen by file type), transform (domain metadata from the folder
 * name), load (vector store). The domain is the parent folder: corpus/policy/x.md gives domain=policy.
 */
public class IngestionService {

    private final List<DocumentParser> parsers;
    private final VectorStore vectorStore;

    public IngestionService(List<DocumentParser> parsers, VectorStore vectorStore) {
        this.parsers = parsers;
        this.vectorStore = vectorStore;
    }

    public List<Document> toDocuments(String domain, String filename, String content) {
        DocumentParser parser = parsers.stream().filter(p -> p.supports(filename)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no parser for " + filename));
        return parser.parse(filename, content).stream()
                .map(d -> d.mutate().metadata("domain", domain).build())
                .toList();
    }

    public int ingest(String domain, String filename, String content) {
        List<Document> docs = toDocuments(domain, filename, content);
        vectorStore.add(docs);
        return docs.size();
    }

    /** Ingest every file under {@code classpath:corpus/<domain>/}. Returns the number of chunks. */
    public int ingestClasspathCorpus() {
        return ingestClasspathCorpus(new PathMatchingResourcePatternResolver());
    }

    /**
     * Same as {@link #ingestClasspathCorpus()} with an explicit resolver (tests use one backed by a jar).
     * Inside a packaged jar the pattern also matches directory entries such as {@code corpus/policy/},
     * which have no file name; those are skipped.
     */
    public int ingestClasspathCorpus(ResourcePatternResolver resolver) {
        try {
            Resource[] files = resolver.getResources("classpath*:corpus/*/*");
            List<Document> all = new ArrayList<>();
            for (Resource r : files) {
                String path = r.getURL().getPath();
                String filename = r.getFilename();
                if (filename == null || filename.isBlank() || path.endsWith("/")) {
                    continue; // a directory entry, not a document
                }
                String[] parts = path.split("/");
                String domain = parts[parts.length - 2];
                all.addAll(toDocuments(domain, filename, r.getContentAsString(StandardCharsets.UTF_8)));
            }
            vectorStore.add(all);
            return all.size();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
