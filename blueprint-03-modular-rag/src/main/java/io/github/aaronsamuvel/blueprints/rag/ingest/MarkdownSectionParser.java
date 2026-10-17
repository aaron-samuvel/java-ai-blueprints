package io.github.aaronsamuvel.blueprints.rag.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;

/** Splits Markdown on top-level and second-level headings; one document per section. */
public class MarkdownSectionParser implements DocumentParser {

    @Override
    public boolean supports(String filename) {
        return filename.endsWith(".md") || filename.endsWith(".txt");
    }

    @Override
    public List<Document> parse(String filename, String content) {
        List<Document> docs = new ArrayList<>();
        String title = "intro";
        StringBuilder body = new StringBuilder();
        for (String line : content.split("\\R")) {
            if (line.startsWith("# ") || line.startsWith("## ")) {
                flush(docs, filename, title, body);
                title = line.replaceFirst("^#+\\s*", "").strip();
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        flush(docs, filename, title, body);
        return docs;
    }

    private static void flush(List<Document> docs, String file, String title, StringBuilder body) {
        String text = body.toString().strip();
        if (text.isEmpty()) return;
        docs.add(new Document(title + "\n" + text, Map.of("source", file, "section", title)));
    }
}
