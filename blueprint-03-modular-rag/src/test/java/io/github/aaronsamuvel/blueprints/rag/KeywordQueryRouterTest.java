package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aaronsamuvel.blueprints.rag.routing.KeywordQueryRouter;
import io.github.aaronsamuvel.blueprints.rag.routing.QueryIntent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeywordQueryRouterTest {

    @ParameterizedTest
    @CsvSource({
            "hello, SMALLTALK",
            "thanks a lot, SMALLTALK",
            "How many days do I have for a refund?, POLICY",
            "How much sick leave do I get?, POLICY",
            "Kafka consumer lag is growing, TECHNICAL",
            "Spring Boot says port 8080 already in use, TECHNICAL",
            "What is the capital of France?, GENERAL"})
    void routes(String query, QueryIntent expected) {
        assertThat(new KeywordQueryRouter().route(query)).isEqualTo(expected);
    }
}
