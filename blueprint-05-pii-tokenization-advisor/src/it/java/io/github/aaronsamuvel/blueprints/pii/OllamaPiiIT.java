package io.github.aaronsamuvel.blueprints.pii;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Live test against a local Ollama (llama3.2:3b). Run: mvn -Pit verify -pl blueprint-05-pii-tokenization-advisor -am
 * The model's wording is non-deterministic, so we only assert the answer is non-empty and the
 * call path works; PII-never-sent is proven deterministically by the unit tests.
 */
@SpringBootTest(properties = "blueprints.model=ollama")
class OllamaPiiIT {

    @Autowired
    PrivacyChatService service;

    @Test
    void liveModelAnswersThroughAdvisor() {
        String answer = service.chat("My email is test.user@example.com. Repeat my email back to me.");
        assertThat(answer).isNotBlank();
    }
}
