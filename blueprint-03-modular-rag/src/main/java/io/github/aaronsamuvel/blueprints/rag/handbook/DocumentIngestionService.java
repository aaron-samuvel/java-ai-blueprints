package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import io.github.aaronsamuvel.blueprints.rag.ingest.MarkdownSectionParser;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Multi-format ingestion for the handbook RAG pipeline.
 * <ul>
 *   <li><b>Extract</b>: Markdown/text by heading ({@link MarkdownSectionParser}); PDF page by page
 *       ({@link PagePdfDocumentReader}, so citations carry page numbers); Word, PowerPoint, Excel, HTML
 *       and the rest through Apache Tika ({@link TikaDocumentReader}).</li>
 *   <li><b>Transform</b>: token-based chunking for long pages, then metadata: {@code filename},
 *       {@code domain_category}, {@code effective_date}, {@code content_hash}, {@code section}, {@code page},
 *       plus optional LLM enrichment ({@link SummaryMetadataEnricher}, {@link KeywordMetadataEnricher}).</li>
 *   <li><b>Load</b>: idempotent by content hash. The same bytes are never embedded twice; a changed file
 *       replaces its old chunks. Chunk ids are derived from hash and position, so they are stable.</li>
 * </ul>
 * The ledger of indexed hashes is kept in memory (it is rebuilt by re-ingesting on startup); with
 * pgvector the same check can query the {@code content_hash} metadata column instead.
 */
public class DocumentIngestionService {

    private record LedgerEntry(String hash, List<String> ids) {}

    private final VectorStore vectorStore;
    private final ChatModel enrichmentModel;
    private final MarkdownSectionParser markdown = new MarkdownSectionParser();
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(400).build();
    private final Map<String, LedgerEntry> ledger = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    /** @param enrichmentModel model for summary and keyword enrichment, or {@code null} to skip enrichment */
    public DocumentIngestionService(VectorStore vectorStore, ChatModel enrichmentModel) {
        this.vectorStore = vectorStore;
        this.enrichmentModel = enrichmentModel;
    }

    public IngestionResult ingest(String filename, byte[] content, DomainCategory category, LocalDate effectiveDate) {
        if (filename == null || filename.isBlank()) throw new IllegalArgumentException("filename is required");
        if (content == null || content.length == 0) throw new IllegalArgumentException("file is empty");
        String hash = sha256(content);
        writeLock.lock();
        try {
            LedgerEntry existing = ledger.get(filename);
            if (existing != null && existing.hash().equals(hash)) {
                return new IngestionResult(filename, hash, IngestionResult.Status.UNCHANGED, existing.ids().size(), readerFor(filename));
            }
            List<Document> chunks = toChunks(filename, content, category, effectiveDate, hash);
            if (existing != null) vectorStore.delete(existing.ids());
            vectorStore.add(chunks);
            ledger.put(filename, new LedgerEntry(hash, chunks.stream().map(Document::getId).toList()));
            return new IngestionResult(filename, hash, existing == null ? IngestionResult.Status.NEW : IngestionResult.Status.REPLACED,
                    chunks.size(), readerFor(filename));
        } finally {
            writeLock.unlock();
        }
    }

    List<Document> toChunks(String filename, byte[] content, DomainCategory category, LocalDate effectiveDate, String hash) {
        List<Document> extracted = extract(filename, content);
        List<Document> chunks = new ArrayList<>();
        for (Document page : extracted) {
            List<Document> pieces = page.getText() != null && page.getText().length() > 2_000 ? splitter.apply(List.of(page)) : List.of(page);
            chunks.addAll(pieces);
        }
        List<Document> withMetadata = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document c = chunks.get(i);
            Map<String, Object> md = new HashMap<>(c.getMetadata());
            md.put("filename", filename);
            md.put("source", filename);
            md.put("domain_category", category.name());
            md.put("effective_date", effectiveDate == null ? "unknown" : effectiveDate.toString());
            md.put("content_hash", hash);
            md.put("chunk_index", i);
            md.putIfAbsent("section", "part " + (i + 1));
            String id = UUID.nameUUIDFromBytes((hash + ":" + i).getBytes(StandardCharsets.UTF_8)).toString();
            withMetadata.add(Document.builder().id(id).text(c.getText()).metadata(md).build());
        }
        if (enrichmentModel != null && !withMetadata.isEmpty()) {
            withMetadata = new KeywordMetadataEnricher(enrichmentModel, 5).apply(withMetadata);
            withMetadata = new SummaryMetadataEnricher(enrichmentModel, List.of(SummaryMetadataEnricher.SummaryType.CURRENT)).apply(withMetadata);
        }
        return withMetadata;
    }

    private List<Document> extract(String filename, byte[] content) {
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return markdown.parse(filename, new String(content, StandardCharsets.UTF_8));
        }
        if (lower.endsWith(".pdf")) {
            List<Document> pages = new PagePdfDocumentReader(resource).get();
            List<Document> out = new ArrayList<>();
            for (Document p : pages) {
                if (p.getText() == null || p.getText().isBlank()) continue;
                Object pageNumber = p.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
                Map<String, Object> md = new HashMap<>();
                md.put("page", pageNumber);
                md.put("section", "page " + pageNumber);
                out.add(new Document(p.getText().replaceAll("[ \\t\\x0B\\f]+", " ").replaceAll("\\s*\\n\\s*", "\n").strip(), md));
            }
            return out;
        }
        return new TikaDocumentReader(resource).get().stream()
                .filter(d -> d.getText() != null && !d.getText().isBlank())
                .map(d -> new Document(d.getText(), Map.of()))
                .toList();
    }

    static String readerFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt")) return "markdown";
        if (lower.endsWith(".pdf")) return "pdf-pages";
        return "tika";
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
