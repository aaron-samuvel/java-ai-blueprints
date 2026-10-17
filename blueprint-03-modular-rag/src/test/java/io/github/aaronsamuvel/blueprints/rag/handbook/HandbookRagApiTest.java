package io.github.aaronsamuvel.blueprints.rag.handbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HandbookRagApiTest {

    @Autowired
    Environment env;

    private RestClient http() {
        return RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
    }

    private JsonNode upload(String filename, String content) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("domainCategory", "runbooks");
        form.add("effectiveDate", "2026-09-30");
        return http().post().uri("/api/v1/rag/ingest").contentType(MediaType.MULTIPART_FORM_DATA).body(form)
                .retrieve().body(JsonNode.class);
    }

    @Test
    void ingestIsIdempotentAndTheNewRunbookIsAnswerable() {
        String runbook = "# Runbook: certificate expiry\n## Steps\nRenew the expiring TLS certificate with certbot renew, then restart the ingress pods.";
        assertThat(upload("cert-expiry-runbook.md", runbook).get("status").asString()).isEqualTo("NEW");
        assertThat(upload("cert-expiry-runbook.md", runbook).get("status").asString()).isEqualTo("UNCHANGED");

        JsonNode answer = http().post().uri("/api/v1/rag/query").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "Which runbook steps renew an expiring TLS certificate during an outage?"))
                .retrieve().body(JsonNode.class);
        assertThat(answer.get("abstainedDueToLowContext").asBoolean()).isFalse();
        assertThat(answer.get("answerMarkdown").asString()).contains("certbot renew");
        assertThat(answer.get("citations").get(0).get("filename").asString()).isEqualTo("cert-expiry-runbook.md");
        assertThat(answer.get("citations").get(0).get("effectiveDate").asString()).isEqualTo("2026-09-30");
    }

    @Test
    void aiEnvironmentHealthAndVectorSearchMetricsAreExposed() {
        JsonNode health = http().get().uri("/actuator/health/aiEnvironment").retrieve().body(JsonNode.class);
        assertThat(health.get("status").asString()).isEqualTo("UP");
        assertThat(health.get("details").toString()).contains("vectorStore").contains("chatModel");

        http().post().uri("/api/v1/rag/query").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("question", "What does the data retention policy say about PII encryption?")).retrieve().toBodilessEntity();
        JsonNode metric = http().get().uri("/actuator/metrics/" + BranchingDocumentRetriever.TIMER).retrieve().body(JsonNode.class);
        assertThat(metric.get("name").asString()).isEqualTo(BranchingDocumentRetriever.TIMER);
    }
}
