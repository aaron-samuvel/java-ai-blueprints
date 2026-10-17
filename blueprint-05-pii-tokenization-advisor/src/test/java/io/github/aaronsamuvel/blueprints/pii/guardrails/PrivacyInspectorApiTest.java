package io.github.aaronsamuvel.blueprints.pii.guardrails;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrivacyInspectorApiTest {

    @LocalServerPort
    int port;

    private JsonNode inspect(Map<String, Object> body) {
        return RestClient.create("http://localhost:" + port).post().uri("/api/v1/privacy/inspect-roundtrip")
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.isString() ? n.asString() : n.toString()));
        return out;
    }

    private static String llmText(JsonNode report) {
        return report.get("llmVisiblePrompts").toString();
    }

    @Test
    void complianceAgentToolGetsTheRealAccountWhileTheModelOnlySeesPlaceholders() {
        JsonNode r = inspect(Map.of("message", "Customer Priya Sharma asks for the balance of account 123456789012",
                "simulatedRoles", List.of("ROLE_COMPLIANCE_AGENT"), "responsePolicy", "DETOKENIZE_FOR_AUTHORIZED_USER"));

        assertThat(strings(r.get("toolsOfferedToLlm"))).containsExactlyInAnyOrder("accountBalance", "branchHours");
        assertThat(strings(r.get("toolsHiddenFromLlm"))).containsExactly("customerContact");
        assertThat(llmText(r)).contains("<PII:ACCOUNT_1>").contains("<PII:PERSON_1>")
                .doesNotContain("123456789012").doesNotContain("Priya").doesNotContain("priya.sharma@example.com");

        JsonNode d = r.get("toolDisclosures").get(0);
        assertThat(d.get("tool").asString()).isEqualTo("accountBalance");
        assertThat(d.get("argumentsSeenByLlm").asString()).contains("<PII:ACCOUNT_1>");
        assertThat(d.get("argumentsDisclosedToTool").asString()).contains("123456789012");
        assertThat(d.get("resultReturnedToLlm").asString()).contains("<PII:PERSON_1>").contains("<PII:EMAIL_1>")
                .doesNotContain("Priya").doesNotContain("@example.com");

        // Caller has no ROLE_PII_VIEWER, so the final answer is redacted, not detokenised.
        assertThat(r.get("finalOutput").asString()).contains("[REDACTED_PERSON]").doesNotContain("Priya").doesNotContain("<PII:");
        JsonNode v = r.get("verdict");
        assertThat(v.get("zeroRawPiiCrossedLlmBoundary").asBoolean()).isTrue();
        assertThat(v.get("modelCalls").asInt()).isEqualTo(2);
        assertThat(strings(r.get("detections"))).anyMatch(s -> s.contains("ACCOUNT_NUMBER") && s.contains("regex"))
                .anyMatch(s -> s.contains("PERSON") && s.contains("opennlp"));
    }

    @Test
    void authorizedViewerGetsTheDetokenisedAnswer() {
        JsonNode r = inspect(Map.of("message", "What is the balance of account 123456789012?",
                "simulatedRoles", List.of("ROLE_COMPLIANCE_AGENT", "ROLE_PII_VIEWER")));
        assertThat(r.get("finalOutput").asString()).contains("123456789012").contains("Priya Sharma").contains("priya.sharma@example.com");
        assertThat(r.get("verdict").get("zeroRawPiiCrossedLlmBoundary").asBoolean()).isTrue();
    }

    @Test
    void withoutTheRoleTheToolIsNotInTheSchemaAndNothingIsDisclosed() {
        JsonNode r = inspect(Map.of("message", "What is the balance of account 123456789012?", "simulatedRoles", List.of()));
        assertThat(strings(r.get("toolsOfferedToLlm"))).containsExactly("branchHours");
        assertThat(r.get("toolDisclosures")).isEmpty();
        assertThat(r.get("finalOutput").asString()).contains("no permitted tool");
        assertThat(r.get("verdict").get("zeroRawPiiCrossedLlmBoundary").asBoolean()).isTrue();
    }

    @Test
    void ragDocumentsAreTokenisedBeforeTheModelSeesThem() {
        JsonNode r = inspect(Map.of("message", "Summarise the KYC note",
                "documents", List.of("KYC note: Rahul Verma, IBAN DE89 3704 0044 0532 0130 00, SSN 123-45-6789, rahul.v@example.org, lives in Pune"),
                "simulatedRoles", List.of(), "responsePolicy", "REDACT"));
        assertThat(llmText(r)).contains("<PII:IBAN_1>").contains("<PII:SSN_1>").contains("<PII:EMAIL_1>").contains("<PII:LOCATION_1>")
                .doesNotContain("DE89").doesNotContain("123-45-6789").doesNotContain("Rahul").doesNotContain("Pune");
        assertThat(r.get("verdict").get("zeroRawPiiCrossedLlmBoundary").asBoolean()).isTrue();
        assertThat(r.get("verdict").get("protectedValues").asInt()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void blockOnLeakBlocksAnAnswerContainingRawPii() {
        JsonNode r = inspect(Map.of("message", "simulate-leak please", "simulatedRoles", List.of("ROLE_PII_VIEWER"),
                "responsePolicy", "BLOCK_ON_LEAK"));
        assertThat(r.get("blocked").asBoolean()).isTrue();
        assertThat(r.get("finalOutput").asString()).startsWith("[blocked").doesNotContain("ravi.kumar@example.net");
        assertThat(strings(r.get("notes"))).anyMatch(n -> n.contains("EMAIL"));
    }

    @Test
    void redactPolicyRedactsEvenForAViewer() {
        JsonNode r = inspect(Map.of("message", "Balance for account 987654321098", "simulatedRoles",
                List.of("ROLE_COMPLIANCE_AGENT", "ROLE_PII_VIEWER"), "responsePolicy", "REDACT"));
        assertThat(r.get("finalOutput").asString()).contains("[REDACTED_ACCOUNT]").doesNotContain("987654321098").doesNotContain("Rahul");
    }

    @Test
    void healthReportsThePresidioProbe() {
        JsonNode h = RestClient.create("http://localhost:" + port).get().uri("/actuator/health/aiEnvironment")
                .retrieve().body(JsonNode.class);
        assertThat(h.get("details").has("presidio")).isTrue();
    }
}
