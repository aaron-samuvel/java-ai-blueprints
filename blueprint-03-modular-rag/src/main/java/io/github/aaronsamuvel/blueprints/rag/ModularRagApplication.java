package io.github.aaronsamuvel.blueprints.rag;

import java.util.List;

import io.github.aaronsamuvel.blueprints.rag.ingest.DoclingStubParser;
import io.github.aaronsamuvel.blueprints.rag.ingest.IngestionService;
import io.github.aaronsamuvel.blueprints.rag.ingest.MarkdownSectionParser;
import io.github.aaronsamuvel.blueprints.rag.routing.KeywordQueryRouter;
import io.github.aaronsamuvel.blueprints.rag.routing.QueryRouter;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;
import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;
import io.github.aaronsamuvel.blueprints.shared.env.AiEnvironmentConfiguration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Offline by default: stub chat model, deterministic stub embeddings, in-memory SimpleVectorStore.
 * For pgvector + Ollama, provide your own EmbeddingModel/VectorStore beans (see PgVectorRagIT).
 */
@SpringBootApplication
@Import({StubModelConfiguration.class, AiEnvironmentConfiguration.class})
public class ModularRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModularRagApplication.class, args);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    EmbeddingModel stubEmbeddingModel() {
        return new StubEmbeddingModel(256);
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    QueryRouter queryRouter() {
        return new KeywordQueryRouter();
    }

    @Bean
    IngestionService ingestionService(VectorStore vectorStore) {
        return new IngestionService(List.of(new MarkdownSectionParser(), new DoclingStubParser()), vectorStore);
    }

    @Bean
    ModularRagService modularRagService(ChatModel chatModel, VectorStore vectorStore, QueryRouter router,
                                        @Value("${blueprints.rag.top-k:3}") int topK,
                                        @Value("${blueprints.rag.similarity-threshold:0.2}") double threshold) {
        return new ModularRagService(chatModel, vectorStore, router, topK, threshold);
    }

    @Bean
    ApplicationRunner ingestCorpusOnStartup(IngestionService ingestion,
                                            @Value("${blueprints.rag.ingest-on-startup:true}") boolean enabled) {
        return args -> {
            if (enabled) ingestion.ingestClasspathCorpus();
        };
    }
}
