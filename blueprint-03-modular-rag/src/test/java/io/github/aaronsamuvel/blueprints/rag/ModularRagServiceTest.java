package io.github.aaronsamuvel.blueprints.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import io.github.aaronsamuvel.blueprints.rag.ingest.IngestionService;
import io.github.aaronsamuvel.blueprints.rag.ingest.MarkdownSectionParser;
import io.github.aaronsamuvel.blueprints.rag.routing.KeywordQueryRouter;
import io.github.aaronsamuvel.blueprints.rag.routing.QueryIntent;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SimpleVectorStore;

class ModularRagServiceTest {

    private StubChatModel model;
    private ModularRagService rag;

    @BeforeEach
    void setUp() {
        SimpleVectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(256)).build();
        new IngestionService(List.of(new MarkdownSectionParser()), store).ingestClasspathCorpus();
        model = StubChatModel.echo();
        rag = new ModularRagService(model, store, new KeywordQueryRouter(), 3, 0.2);
    }

    @Test
    void policyQuestionIsGroundedOnlyInPolicyDocs() {
        RagAnswer a = rag.ask("How many days do I have to request a refund?");
        assertThat(a.intent()).isEqualTo(QueryIntent.POLICY);
        assertThat(a.sources()).contains("refund-policy.md#Refund policy")
                .allMatch(s -> s.startsWith("refund-policy.md") || s.startsWith("leave-policy.md"));
        String sent = model.allSentText();
        assertThat(sent).contains("within 30 days of delivery");
        assertThat(sent).doesNotContain("Kafka").doesNotContain("server.port");
    }

    @Test
    void technicalQuestionIsGroundedOnlyInTechnicalDocs() {
        RagAnswer a = rag.ask("Kafka consumer lag alert, what should I check?");
        assertThat(a.intent()).isEqualTo(QueryIntent.TECHNICAL);
        assertThat(a.sources()).contains("kafka-runbook.md#Consumer lag alert")
                .noneMatch(s -> s.contains("policy"));
        assertThat(model.allSentText()).contains("rebalancing").doesNotContain("refund");
    }

    @Test
    void smalltalkSkipsRetrieval() {
        RagAnswer a = rag.ask("hello");
        assertThat(a.intent()).isEqualTo(QueryIntent.SMALLTALK);
        assertThat(a.sources()).isEmpty();
        assertThat(model.allSentText()).doesNotContain("Context information").doesNotContain("refund");
    }

    @Test
    void noRelevantContextMakesModelDeclineInsteadOfHallucinating() {
        RagAnswer a = rag.ask("Quantum chromodynamics gluon confinement");
        assertThat(a.intent()).isEqualTo(QueryIntent.GENERAL);
        assertThat(a.sources()).isEmpty();
        // ContextualQueryAugmenter(allowEmptyContext=false) replaces the query with a decline instruction
        assertThat(model.allSentText()).doesNotContain("gluon");
    }
}
