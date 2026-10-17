package io.github.aaronsamuvel.blueprints.pii.guardrails;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.CompositePiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.OpenNlpPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiSpan;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PresidioPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.RegexPiiAnalyzer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiAnalyzersTest {

    private static List<PiiEntityType> types(List<PiiSpan> spans) {
        return spans.stream().map(PiiSpan::type).toList();
    }

    @Test
    void regexRecognizersValidateChecksumsAndContext() {
        RegexPiiAnalyzer regex = new RegexPiiAnalyzer();
        assertThat(types(regex.analyze("mail asha@example.com or call +91 98765 43210"))).contains(PiiEntityType.EMAIL, PiiEntityType.PHONE);
        assertThat(types(regex.analyze("card 4111 1111 1111 1111"))).contains(PiiEntityType.CREDIT_CARD);
        assertThat(types(regex.analyze("card 4111 1111 1111 1112"))).doesNotContain(PiiEntityType.CREDIT_CARD);
        assertThat(types(regex.analyze("SSN 123-45-6789"))).containsExactly(PiiEntityType.SSN);
        assertThat(types(regex.analyze("PAN ABCDE1234F"))).containsExactly(PiiEntityType.PAN);
        assertThat(regex.analyze("IBAN DE89 3704 0044 0532 0130 00").get(0).type()).isEqualTo(PiiEntityType.IBAN);
        assertThat(types(regex.analyze("IBAN DE88 3704 0044 0532 0130 00"))).doesNotContain(PiiEntityType.IBAN);
        PiiSpan acct = regex.analyze("transfer from account no. 123456789012 today").get(0);
        assertThat(acct.type()).isEqualTo(PiiEntityType.ACCOUNT_NUMBER);
        assertThat(acct.text()).isEqualTo("123456789012");
        assertThat(regex.analyze("order 123456789012 shipped")).isEmpty();
    }

    @Test
    void openNlpFindsPersonsAndLocationsWithTheDictionaryNameFinder() {
        OpenNlpPiiAnalyzer nlp = new OpenNlpPiiAnalyzer();
        List<PiiSpan> spans = nlp.analyze("Priya Sharma moved from Chennai to New Delhi.");
        assertThat(spans).extracting(PiiSpan::text).contains("Priya Sharma", "Chennai", "New Delhi");
        assertThat(spans.stream().filter(s -> s.text().equals("Priya Sharma")).findFirst().orElseThrow().type()).isEqualTo(PiiEntityType.PERSON);
        assertThat(nlp.statisticalFinders()).isZero();
    }

    @Test
    void compositeResolvesOverlapsAndIgnoresExistingPlaceholders() {
        CompositePiiAnalyzer all = new CompositePiiAnalyzer(List.of(new RegexPiiAnalyzer(), new OpenNlpPiiAnalyzer()));
        List<PiiSpan> spans = all.analyze("Priya Sharma, priya.sharma@example.com, <PII:PERSON_1>");
        assertThat(spans).extracting(PiiSpan::text).containsExactly("Priya Sharma", "priya.sharma@example.com");
        for (int i = 1; i < spans.size(); i++) assertThat(spans.get(i - 1).overlaps(spans.get(i))).isFalse();
    }

    @Test
    void presidioClientMapsEntitiesAndFailsClosed() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/analyze", ex -> {
            byte[] body = "[{\"entity_type\":\"PERSON\",\"start\":0,\"end\":5,\"score\":0.85},{\"entity_type\":\"URL\",\"start\":6,\"end\":9,\"score\":0.9},{\"entity_type\":\"US_SSN\",\"start\":10,\"end\":21,\"score\":0.3}]"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            PresidioPiiAnalyzer presidio = new PresidioPiiAnalyzer("http://127.0.0.1:" + server.getAddress().getPort(), 0.5, true);
            List<PiiSpan> spans = presidio.analyze("Meera x.y 123-45-6789");
            assertThat(spans).singleElement().satisfies(s -> {
                assertThat(s.type()).isEqualTo(PiiEntityType.PERSON);
                assertThat(s.text()).isEqualTo("Meera");
                assertThat(s.analyzer()).isEqualTo("presidio");
            });
        } finally {
            server.stop(0);
        }
        PresidioPiiAnalyzer down = new PresidioPiiAnalyzer("http://127.0.0.1:1", 0.5, true);
        assertThatThrownBy(() -> down.analyze("hello")).isInstanceOf(IllegalStateException.class);
        assertThat(new PresidioPiiAnalyzer("http://127.0.0.1:1", 0.5, false).analyze("hello")).isEmpty();
    }
}
