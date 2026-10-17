package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.util.Set;

import javax.sql.DataSource;

import io.github.aaronsamuvel.blueprints.nl2sql.workflow.ContextCompactor;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Offline wiring: {@link DataAgentDemoModel} (kept out of the context as a bean so it does not
 * compete with the app's ChatModel), stub embeddings and an in-memory vector store.
 */
@Configuration(proxyBeanMethods = false)
public class DataAgentConfiguration {

    @Bean
    SchemaVectorIndexer schemaVectorIndexer(DataSource ds, @Value("${blueprints.data-agent.max-enum-values:5}") int maxEnum) {
        return new SchemaVectorIndexer(ds, SimpleVectorStore.builder(new StubEmbeddingModel(256)).build(), "SHOP_", maxEnum);
    }

    @Bean
    ApplicationRunner indexSchema(SchemaVectorIndexer indexer) {
        return args -> indexer.index();
    }

    @Bean
    SqlAstGuardrailValidator sqlAstGuardrailValidator(@Value("${blueprints.nl2sql.max-rows:500}") long maxRows) {
        return new SqlAstGuardrailValidator(Set.of("SHOP_CUSTOMERS", "SHOP_PRODUCTS", "SHOP_ORDERS", "SHOP_ORDER_LINES"), maxRows);
    }

    @Bean
    ContextCompactor dataAgentCompactor(@Value("${blueprints.data-agent.compaction-tokens:600}") int tokens) {
        return new ContextCompactor(tokens, 2);
    }

    @Bean
    SelfHealingSqlLoopAgent selfHealingSqlLoopAgent(SqlAstGuardrailValidator guard, DataSource ds, PlatformTransactionManager tx,
                                                    ContextCompactor compactor,
                                                    @Value("${blueprints.nl2sql.max-retries:3}") int retries,
                                                    @Value("${blueprints.data-agent.max-full-scan-rows:100000}") long maxScan) {
        TransactionTemplate ro = new TransactionTemplate(tx);
        ro.setReadOnly(true);
        ro.setTimeout(10);
        return new SelfHealingSqlLoopAgent(ChatClient.create(new DataAgentDemoModel()), guard, JdbcClient.create(ds), ro, retries, maxScan, compactor);
    }

    @Bean
    DataAgentService dataAgentService(SchemaVectorIndexer indexer, SelfHealingSqlLoopAgent loop, ContextCompactor compactor,
                                      @Value("${blueprints.data-agent.top-k:2}") int topK,
                                      @Value("${blueprints.data-agent.max-tool-calls:12}") int maxToolCalls) {
        return new DataAgentService(indexer, loop, new ExecutiveInsightSynthesizer(), compactor, topK, maxToolCalls);
    }
}
