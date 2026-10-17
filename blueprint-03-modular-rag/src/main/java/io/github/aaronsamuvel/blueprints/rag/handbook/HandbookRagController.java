package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** {@code POST /api/v1/rag/ingest} (multipart) and {@code POST /api/v1/rag/query}. */
@RestController
@RequestMapping("/api/v1/rag")
public class HandbookRagController {

    public record QueryRequest(String question) {}

    private final DocumentIngestionService ingestion;
    private final HandbookRagService rag;

    public HandbookRagController(DocumentIngestionService ingestion, HandbookRagService rag) {
        this.ingestion = ingestion;
        this.rag = rag;
    }

    @PostMapping(path = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionResult ingest(@RequestPart("file") MultipartFile file,
                                  @RequestParam("domainCategory") String domainCategory,
                                  @RequestParam(name = "effectiveDate", required = false) String effectiveDate) throws IOException {
        DomainCategory category;
        LocalDate date;
        try {
            category = DomainCategory.valueOf(domainCategory.strip().toUpperCase(Locale.ROOT));
            date = effectiveDate == null || effectiveDate.isBlank() ? null : LocalDate.parse(effectiveDate.strip());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "domainCategory must be ARCHITECTURE, SECURITY_POLICY or RUNBOOKS and effectiveDate yyyy-MM-dd");
        }
        try {
            return ingestion.ingest(file.getOriginalFilename(), file.getBytes(), category, date);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/query")
    public GroundedAnswer query(@RequestBody QueryRequest request) {
        try {
            return rag.ask(request.question());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
