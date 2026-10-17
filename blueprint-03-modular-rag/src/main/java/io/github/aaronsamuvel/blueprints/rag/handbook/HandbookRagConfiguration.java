package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aaronsamuvel.blueprints.shared.env.VectorStoreProbe;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Wires the handbook pipeline. With {@code blueprints.model=stub} (default) the LLM steps use
 * {@link HandbookDemoModel}; otherwise the application's ChatModel. The similarity threshold defaults
 * to the handbook's 0.76 for real embeddings and to 0.25 for the offline bag-of-words stub embeddings,
 * whose scores are lower by construction (property {@code blueprints.rag.handbook.similarity-threshold}).
 */
@Configuration(proxyBeanMethods = false)
public class HandbookRagConfiguration {

    private static final Log log = LogFactory.getLog(HandbookRagConfiguration.class);
    private static final Pattern EFFECTIVE = Pattern.compile("(?m)^effective_date:\\s*(\\d{4}-\\d{2}-\\d{2})\\s*$");

    private static ChatModel model(String mode, ObjectProvider<ChatModel> chatModel) {
        return "stub".equals(mode) ? HandbookDemoModel.create() : chatModel.getObject();
    }

    @Bean
    DocumentIngestionService documentIngestionService(VectorStore vectorStore, ObjectProvider<ChatModel> chatModel,
                                                      @Value("${blueprints.model:stub}") String mode,
                                                      @Value("${blueprints.rag.handbook.enrich:true}") boolean enrich) {
        return new DocumentIngestionService(vectorStore, enrich ? model(mode, chatModel) : null);
    }

    @Bean
    BranchingDocumentRetriever branchingDocumentRetriever(VectorStore vectorStore, ObjectProvider<MeterRegistry> meters,
                                                          @Value("${blueprints.model:stub}") String mode,
                                                          @Value("${blueprints.rag.handbook.similarity-threshold:}") String threshold,
                                                          @Value("${blueprints.rag.top-k:3}") int topK) {
        double floor = !threshold.isBlank() ? Double.parseDouble(threshold) : "stub".equals(mode) ? 0.25 : 0.76;
        return new BranchingDocumentRetriever(vectorStore, topK, floor, meters.getIfAvailable(SimpleMeterRegistry::new),
                e -> log.debug("retrieval " + e));
    }

    @Bean
    HandbookRagService handbookRagService(ObjectProvider<ChatModel> chatModel, BranchingDocumentRetriever retriever,
                                          @Value("${blueprints.model:stub}") String mode,
                                          @Value("${blueprints.rag.handbook.max-documents:4}") int maxDocuments) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("rag-retrieval-");
        executor.setVirtualThreads(true);
        return new HandbookRagService(model(mode, chatModel), retriever, maxDocuments, executor);
    }

    @Bean
    VectorStoreProbe vectorStoreProbe(ObjectProvider<VectorStore> vectorStore) {
        return new VectorStoreProbe(vectorStore::getIfUnique);
    }

    /** Ingest {@code classpath:handbook-corpus/<CATEGORY>/*}; a line {@code effective_date: yyyy-MM-dd} sets the date. */
    @Bean
    ApplicationRunner ingestHandbookCorpus(DocumentIngestionService ingestion,
                                           @Value("${blueprints.rag.ingest-on-startup:true}") boolean enabled) {
        return args -> {
            if (!enabled) return;
            int files = 0;
            try {
                for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:handbook-corpus/*/*")) {
                    String path = r.getURL().getPath();
                    if (r.getFilename() == null || r.getFilename().isBlank() || path.endsWith("/")) continue;
                    String[] parts = path.split("/");
                    String text = r.getContentAsString(StandardCharsets.UTF_8);
                    Matcher m = EFFECTIVE.matcher(text);
                    LocalDate date = m.find() ? LocalDate.parse(m.group(1)) : null;
                    byte[] body = EFFECTIVE.matcher(text).replaceAll("").strip().getBytes(StandardCharsets.UTF_8);
                    ingestion.ingest(r.getFilename(), body, DomainCategory.valueOf(parts[parts.length - 2]), date);
                    files++;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            log.info("Handbook RAG corpus: ingested " + files + " files");
        };
    }
}
