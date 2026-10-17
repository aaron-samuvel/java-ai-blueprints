package io.github.aaronsamuvel.blueprints.hitl.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.micrometer.observation.ObservationRegistry;

import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcCheckpointSaverTest {

    private static SubmissionWorkflowService freshService(DriverManagerDataSource ds, ExecutorService executor,
                                                          JdbcCheckpointSaver[] saverOut) throws Exception {
        JdbcCheckpointSaver saver = new JdbcCheckpointSaver(ds, new ObjectStreamStateSerializer<>(ReviewWorkflowState::new));
        saverOut[0] = saver;
        var graph = SubmissionReviewGraph.build(ChatClient.create(MockEvaluatorModel.create()), saver,
                new NodeExecutionHooks(ObservationRegistry.NOOP, Duration.ofSeconds(10), executor));
        return new SubmissionWorkflowService(graph, executor);
    }

    @Test
    void pausedThreadSurvivesARestart() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:checkpoints;DB_CLOSE_DELAY=-1");
        JdbcCheckpointSaver[] saver = new JdbcCheckpointSaver[1];
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var before = freshService(ds, executor, saver);
            var paused = before.start("SUB-JDBC", Samples.load("mixed"));
            assertThat(paused.awaitingHuman()).isTrue();
            assertThat(saver[0].storedThreads()).isEqualTo(1);

            // "Restart": a new graph and a new saver instance that share nothing in memory with the first.
            var after = freshService(ds, executor, saver);
            var reloaded = after.state(paused.threadId());
            assertThat(reloaded.awaitingHuman()).isTrue();
            assertThat(reloaded.dimensionScores()).hasSize(4);
            assertThat(reloaded.compositeScore()).isEqualTo(paused.compositeScore());

            var done = after.resume(paused.threadId(), new HumanVerdict(true, "lead@example.com", "approved after restart"));
            assertThat(done.status()).isEqualTo("HUMAN_APPROVED");
            assertThat(freshService(ds, executor, saver).state(paused.threadId()).status()).isEqualTo("HUMAN_APPROVED");
        }
    }
}
