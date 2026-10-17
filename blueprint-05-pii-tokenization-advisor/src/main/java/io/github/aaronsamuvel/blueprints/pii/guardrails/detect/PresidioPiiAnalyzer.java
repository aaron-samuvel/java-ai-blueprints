package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client for an optional Microsoft Presidio analyzer container ({@code POST /analyze}). Enabled
 * when {@code blueprints.environment.presidio-url} is set, e.g. by {@code docker-compose.ai-support.yml}.
 * <p>Fails closed by default: if Presidio is configured but unreachable, analysis throws, so text
 * is never sent to a model with a detector silently missing.
 */
public final class PresidioPiiAnalyzer implements PiiAnalyzerSpi {

    /** Shape of one item in Presidio's analyze response. */
    public record PresidioResult(String entity_type, int start, int end, double score) {}

    private static final Map<String, PiiEntityType> MAPPING = Map.ofEntries(
            Map.entry("PERSON", PiiEntityType.PERSON),
            Map.entry("LOCATION", PiiEntityType.LOCATION),
            Map.entry("EMAIL_ADDRESS", PiiEntityType.EMAIL),
            Map.entry("PHONE_NUMBER", PiiEntityType.PHONE),
            Map.entry("CREDIT_CARD", PiiEntityType.CREDIT_CARD),
            Map.entry("US_SSN", PiiEntityType.SSN),
            Map.entry("IBAN_CODE", PiiEntityType.IBAN),
            Map.entry("US_BANK_NUMBER", PiiEntityType.ACCOUNT_NUMBER),
            Map.entry("IN_PAN", PiiEntityType.PAN),
            Map.entry("IN_AADHAAR", PiiEntityType.AADHAAR));

    private final RestClient http;
    private final double minScore;
    private final boolean failClosed;

    public PresidioPiiAnalyzer(String baseUrl, double minScore, boolean failClosed) {
        this.http = RestClient.builder().baseUrl(baseUrl.replaceAll("/+$", "")).build();
        this.minScore = minScore;
        this.failClosed = failClosed;
    }

    @Override
    public String name() {
        return "presidio";
    }

    @Override
    public List<PiiSpan> analyze(String text) {
        List<PiiSpan> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        PresidioResult[] results;
        try {
            results = http.post().uri("/analyze").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text, "language", "en"))
                    .retrieve().body(PresidioResult[].class);
        } catch (RestClientException e) {
            if (failClosed) throw new IllegalStateException("Presidio analyzer unavailable; refusing to continue", e);
            return out;
        }
        if (results == null) return out;
        for (PresidioResult r : results) {
            PiiEntityType type = MAPPING.get(r.entity_type());
            if (type != null && r.score() >= minScore && r.end() > r.start() && r.end() <= text.length()) {
                out.add(new PiiSpan(type, r.start(), r.end(), text.substring(r.start(), r.end()), r.score(), name()));
            }
        }
        return out;
    }
}
