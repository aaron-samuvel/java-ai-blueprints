package io.github.aaronsamuvel.blueprints.rag.handbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

class DocumentIngestionServiceTest {

    private final SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(256)).build();
    private final DocumentIngestionService ingestion = new DocumentIngestionService(store, null);

    private List<Document> all() {
        return store.similaritySearch(SearchRequest.builder().query("x").topK(1000).similarityThresholdAll().build());
    }

    @Test
    void sameBytesAreNotEmbeddedTwiceAndChangedFilesReplaceTheirChunks() {
        byte[] v1 = "# Policy\n## Leave\nStaff get 24 days of leave.\n## Travel\nBook trains in advance.".getBytes(StandardCharsets.UTF_8);
        IngestionResult first = ingestion.ingest("hr.md", v1, DomainCategory.SECURITY_POLICY, LocalDate.of(2026, 1, 1));
        assertThat(first.status()).isEqualTo(IngestionResult.Status.NEW);
        assertThat(first.chunks()).isEqualTo(2);
        assertThat(first.contentHash()).hasSize(64);
        assertThat(all()).hasSize(2);

        IngestionResult again = ingestion.ingest("hr.md", v1, DomainCategory.SECURITY_POLICY, LocalDate.of(2026, 1, 1));
        assertThat(again.status()).isEqualTo(IngestionResult.Status.UNCHANGED);
        assertThat(all()).hasSize(2);

        byte[] v2 = "# Policy\n## Leave\nStaff get 26 days of leave.".getBytes(StandardCharsets.UTF_8);
        IngestionResult replaced = ingestion.ingest("hr.md", v2, DomainCategory.SECURITY_POLICY, LocalDate.of(2026, 7, 1));
        assertThat(replaced.status()).isEqualTo(IngestionResult.Status.REPLACED);
        assertThat(all()).hasSize(1).first().satisfies(d -> {
            assertThat(d.getText()).contains("26 days");
            assertThat(d.getMetadata()).containsEntry("effective_date", "2026-07-01").containsEntry("domain_category", "SECURITY_POLICY")
                    .containsEntry("filename", "hr.md").containsEntry("section", "Leave");
        });
    }

    @Test
    void chunkIdsAreStableForTheSameContent() {
        byte[] doc = "# A\nalpha text here".getBytes(StandardCharsets.UTF_8);
        String hash = DocumentIngestionService.sha256(doc);
        String id1 = ingestion.toChunks("a.md", doc, DomainCategory.ARCHITECTURE, null, hash).getFirst().getId();
        String id2 = ingestion.toChunks("a.md", doc, DomainCategory.ARCHITECTURE, null, hash).getFirst().getId();
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void pdfIsReadPageByPageSoCitationsCarryPageNumbers() throws Exception {
        byte[] pdf;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : List.of("Disaster recovery overview for the ledger.", "Restore the ledger database from the nightly snapshot.")) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            pdf = out.toByteArray();
        }
        IngestionResult r = ingestion.ingest("dr-plan.pdf", pdf, DomainCategory.RUNBOOKS, LocalDate.of(2026, 2, 2));
        assertThat(r.reader()).isEqualTo("pdf-pages");
        assertThat(r.chunks()).isEqualTo(2);
        Document page2 = all().stream().filter(d -> d.getText().contains("nightly snapshot")).findFirst().orElseThrow();
        assertThat(page2.getMetadata()).containsEntry("page", 2).containsEntry("section", "page 2");
        assertThat(HandbookRagService.citation(page2).page()).isEqualTo(2);
    }

    @Test
    void wordDocumentsGoThroughTika() throws Exception {
        byte[] docx;
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Architecture decision: all services emit events through the outbox.");
            doc.write(out);
            docx = out.toByteArray();
        }
        IngestionResult r = ingestion.ingest("adr-9.docx", docx, DomainCategory.ARCHITECTURE, null);
        assertThat(r.reader()).isEqualTo("tika");
        assertThat(all()).anySatisfy(d -> {
            assertThat(d.getText()).contains("emit events through the outbox");
            assertThat(d.getMetadata()).containsEntry("effective_date", "unknown").containsEntry("section", "part 1");
        });
    }

    @Test
    void rejectsEmptyFiles() {
        assertThatThrownBy(() -> ingestion.ingest("empty.md", new byte[0], DomainCategory.RUNBOOKS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
