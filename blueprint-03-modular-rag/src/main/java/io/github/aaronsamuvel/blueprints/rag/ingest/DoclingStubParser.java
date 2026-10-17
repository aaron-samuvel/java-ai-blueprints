package io.github.aaronsamuvel.blueprints.rag.ingest;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * Placeholder for Docling (PDF/DOCX to structured Markdown). The handbook uses Docling for
 * parsing; running it needs a separate Python service, so this repo ships a <b>stub</b>: it
 * accepts {@code .pdf}/{@code .docx} names whose content has already been converted to Markdown
 * text (for example with the Docling CLI) and delegates to {@link MarkdownSectionParser}.
 * No docling-serve HTTP client is implemented; {@code DocumentIngestionService} uses Tika/PDFBox instead (see the module README).
 */
public class DoclingStubParser implements DocumentParser {

    private final MarkdownSectionParser markdown = new MarkdownSectionParser();

    @Override
    public boolean supports(String filename) {
        return filename.endsWith(".pdf") || filename.endsWith(".docx");
    }

    @Override
    public List<Document> parse(String filename, String preConvertedMarkdown) {
        return markdown.parse(filename, preConvertedMarkdown).stream()
                .map(d -> d.mutate().metadata("parser", "docling-stub").build())
                .toList();
    }
}
