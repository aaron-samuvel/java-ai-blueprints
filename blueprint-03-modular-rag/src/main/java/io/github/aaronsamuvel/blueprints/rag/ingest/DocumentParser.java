package io.github.aaronsamuvel.blueprints.rag.ingest;

import java.util.List;

import org.springframework.ai.document.Document;

/** Turns a source file into documents. Implementations: Markdown (default), Docling (optional). */
public interface DocumentParser {
    boolean supports(String filename);

    List<Document> parse(String filename, String content);
}
