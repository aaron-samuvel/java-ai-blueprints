package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import io.micrometer.observation.ObservationRegistry;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the submission review graph.
 * <ul>
 *   <li>{@code llm.provider}: {@code mock} (default, {@link MockEvaluatorModel}); any other value uses the
 *       application's {@link ChatModel} bean, e.g. Ollama with {@code -Pit} and {@code BLUEPRINTS_MODEL=ollama};</li>
 *   <li>{@code blueprints.hitl.checkpoint}: {@code memory} (default, LangGraph4j {@link MemorySaver}) or
 *       {@code jdbc} ({@link JdbcCheckpointSaver} on the configured DataSource, PostgreSQL in production);</li>
 *   <li>{@code blueprints.hitl.node-timeout}: per-node time budget enforced by {@link NodeExecutionHooks}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class SubmissionWorkflowConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService submissionVirtualThreads() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("review-eval-", 0).factory());
    }

    @Bean
    BaseCheckpointSaver submissionCheckpointSaver(@Value("${blueprints.hitl.checkpoint:memory}") String kind,
                                                  ObjectProvider<DataSource> dataSource) {
        return switch (kind) {
            case "memory" -> new MemorySaver();
            case "jdbc" -> new JdbcCheckpointSaver(dataSource.getObject(), new ObjectStreamStateSerializer<>(ReviewWorkflowState::new));
            default -> throw new IllegalArgumentException("blueprints.hitl.checkpoint must be memory or jdbc, not " + kind);
        };
    }

    @Bean
    SubmissionWorkflowService submissionWorkflowService(@Value("${llm.provider:mock}") String provider,
                                                        ObjectProvider<ChatModel> chatModel,
                                                        BaseCheckpointSaver submissionCheckpointSaver,
                                                        ObjectProvider<ObservationRegistry> observations,
                                                        @Value("${blueprints.hitl.node-timeout:PT60S}") Duration nodeTimeout,
                                                        ExecutorService submissionVirtualThreads) throws GraphStateException {
        ChatModel model = "mock".equals(provider) ? MockEvaluatorModel.create() : chatModel.getObject();
        NodeExecutionHooks hooks = new NodeExecutionHooks(observations.getIfAvailable(() -> ObservationRegistry.NOOP),
                nodeTimeout, submissionVirtualThreads);
        return new SubmissionWorkflowService(
                SubmissionReviewGraph.build(ChatClient.create(model), submissionCheckpointSaver, hooks), submissionVirtualThreads);
    }
}
