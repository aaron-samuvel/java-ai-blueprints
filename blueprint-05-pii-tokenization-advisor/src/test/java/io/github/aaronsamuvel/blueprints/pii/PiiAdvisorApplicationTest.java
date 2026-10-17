package io.github.aaronsamuvel.blueprints.pii;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.github.aaronsamuvel.blueprints.finops.FinOpsBudgetAdvisor;
import io.github.aaronsamuvel.blueprints.finops.TokenBudgetLedger;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/** Full Spring context on a random port, real HTTP call, stub model capturing what was sent. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PiiAdvisorApplicationTest {

    @Autowired
    StubChatModel model;

    @Autowired
    Environment env;

    @Autowired
    FinOpsBudgetAdvisor budget;

    @Autowired
    TokenBudgetLedger ledger;

    @Test
    void httpChatEndpointProtectsPiiEndToEnd() {
        String email = "rahul.v@example.org";
        RestClient http = RestClient.create("http://localhost:" + env.getProperty("local.server.port"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = http.post().uri("/api/chat")
                .header("Content-Type", "application/json")
                .body(Map.of("message", "Update my email to " + email))
                .retrieve().body(Map.class);

        assertThat(body.get("answer")).isEqualTo("echo: Update my email to " + email);
        assertThat(model.allSentText()).doesNotContain(email).contains("<<EMAIL_1>>");
        // the FinOps advisor ran in the same chain: reservation settled, usage recorded
        assertThat(budget.records()).isNotEmpty();
        assertThat(ledger.snapshot("default").reserved()).isZero();
        assertThat(ledger.snapshot("default").used()).isPositive();
    }
}
