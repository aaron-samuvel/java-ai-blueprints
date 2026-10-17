package io.github.aaronsamuvel.blueprints.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingRequest;

class StubModelsTest {

    @Test
    void echoModelRecordsPromptsAndWorksThroughChatClient() {
        StubChatModel model = StubChatModel.echo();
        String reply = ChatClient.create(model).prompt().user("hello").call().content();
        assertThat(reply).isEqualTo("echo: hello");
        assertThat(model.received()).hasSize(1);
        assertThat(model.allSentText()).contains("hello");
    }

    @Test
    void embeddingsAreDeterministicAndSimilarityIsSensible() {
        StubEmbeddingModel m = new StubEmbeddingModel();
        float[] a = m.vector("refund policy for orders");
        float[] b = m.vector("orders refund policy");
        float[] c = m.vector("kubernetes pod restart");
        assertThat(m.vector("refund policy for orders")).containsExactly(a);
        assertThat(StubEmbeddingModel.cosine(a, b)).isGreaterThan(StubEmbeddingModel.cosine(a, c));
        assertThat(m.call(new EmbeddingRequest(List.of("x", "y"), null)).getResults()).hasSize(2);
    }
}
