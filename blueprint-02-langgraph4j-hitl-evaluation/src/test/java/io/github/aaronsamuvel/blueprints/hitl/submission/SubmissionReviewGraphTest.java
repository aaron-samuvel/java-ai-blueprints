package io.github.aaronsamuvel.blueprints.hitl.submission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

class SubmissionReviewGraphTest {

    private final ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<String> observedNodes = ConcurrentHashMap.newKeySet();

    @AfterEach
    void close() {
        virtualThreads.close();
    }

    private SubmissionWorkflowService service(ChatModel model, Duration timeout) throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context context) {
                observedNodes.add(context.getLowCardinalityKeyValue("node").getValue());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return NodeExecutionHooks.OBSERVATION.equals(context.getName());
            }
        });
        var graph = SubmissionReviewGraph.build(ChatClient.create(model), new MemorySaver(),
                new NodeExecutionHooks(registry, timeout, virtualThreads));
        return new SubmissionWorkflowService(graph, virtualThreads);
    }

    private SubmissionWorkflowService service() throws Exception {
        return service(MockEvaluatorModel.create(), Duration.ofSeconds(10));
    }

    @Test
    void strongSubmissionIsAutoApproved() throws Exception {
        var v = service().start("SUB-1", Samples.load("strong"));
        assertThat(v.status()).isEqualTo("AUTO_APPROVED");
        assertThat(v.decision()).isEqualTo("AUTO_APPROVE");
        assertThat(v.compositeScore()).isGreaterThanOrEqualTo(85);
        assertThat(v.redFlags()).isEmpty();
        assertThat(v.dimensionScores()).hasSize(4);
        assertThat(v.parsedProfileJson()).contains("\"name\":\"Priya Raman\"").contains("\"experienceYears\":12");
        assertThat(v.nodeLog()).contains("autoApprove");
    }

    @Test
    void weakSubmissionIsAutoRejected() throws Exception {
        var v = service().start(null, Samples.load("weak"));
        assertThat(v.status()).isEqualTo("AUTO_REJECTED");
        assertThat(v.compositeScore()).isLessThan(50);
        assertThat(v.submissionId()).startsWith("SUB-");
    }

    @Test
    void evaluatorDisagreementPausesForAHumanAndResumes() throws Exception {
        SubmissionWorkflowService service = service();
        var paused = service.start("SUB-3", Samples.load("mixed"));
        assertThat(paused.awaitingHuman()).isTrue();
        assertThat(paused.status()).isEqualTo("AWAITING_HUMAN");
        assertThat(paused.decision()).isEqualTo("HUMAN_ESCALATION");
        assertThat(paused.decisionReason()).contains("disagree");
        assertThat(paused.scoreSpread()).isGreaterThan(25);
        assertThat(service.state(paused.threadId()).awaitingHuman()).isTrue();

        var done = service.resume(paused.threadId(), new HumanVerdict(true, "lead@example.com", "strong on search; pair on culture"));
        assertThat(done.status()).isEqualTo("HUMAN_APPROVED");
        assertThat(done.awaitingHuman()).isFalse();
        assertThat(done.humanVerdict().reviewer()).isEqualTo("lead@example.com");
        assertThat(service.state(paused.threadId()).status()).as("final state stays readable").isEqualTo("HUMAN_APPROVED");
        assertThatThrownBy(() -> service.resume(paused.threadId(), new HumanVerdict(false, "x", "")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void criticalRedFlagEscalatesAndHumanCanReject() throws Exception {
        SubmissionWorkflowService service = service();
        var paused = service.start("SUB-4", Samples.load("critical"));
        assertThat(paused.awaitingHuman()).isTrue();
        assertThat(paused.redFlags()).containsExactly("CRITICAL: employment dates are falsified");
        assertThat(paused.compositeScore()).as("scores alone would auto-approve").isGreaterThanOrEqualTo(85);
        assertThat(service.resume(paused.threadId(), new HumanVerdict(false, "hr@example.com", "failed reference check")).status())
                .isEqualTo("HUMAN_REJECTED");
    }

    @Test
    void fourEvaluatorsRunInParallelOnVirtualThreadsUnderObservation() throws Exception {
        var v = service().start("SUB-5", Samples.load("strong"));
        assertThat(v.nodeLog()).contains(
                SkillsAndArchitectureNode.DIMENSION + " on virtual thread",
                ExperienceAndResilienceNode.DIMENSION + " on virtual thread",
                CultureAndGovernanceNode.DIMENSION + " on virtual thread",
                RedFlagAuditNode.DIMENSION + " on virtual thread");
        assertThat(observedNodes).contains(SubmissionReviewGraph.PARSE, SubmissionReviewGraph.CONSENSUS, SubmissionReviewGraph.AUTO_APPROVE);
    }

    @Test
    void evaluatorsOverlapInTime() throws Exception {
        StubChatModel slow = new StubChatModel(p -> {
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MockEvaluatorModel.answer(p);
        });
        SubmissionWorkflowService service = service(slow, Duration.ofSeconds(10));
        long t0 = System.nanoTime();
        assertThat(service.start("SUB-8", Samples.load("strong")).status()).isEqualTo("AUTO_APPROVED");
        long millis = (System.nanoTime() - t0) / 1_000_000;
        // 5 calls of 400 ms: sequential would take >= 2000 ms; parse + one parallel wave takes about 800 ms.
        assertThat(millis).isLessThan(1_600);
    }

    @Test
    void hangingNodeFailsWithATimeout() throws Exception {
        StubChatModel slow = new StubChatModel(p -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return MockEvaluatorModel.answer(p);
        });
        SubmissionWorkflowService service = service(slow, Duration.ofMillis(200));
        assertThatThrownBy(() -> service.start("SUB-6", Samples.load("strong")))
                .hasStackTraceContaining("node '" + SubmissionReviewGraph.PARSE + "' exceeded its timeout of 200 ms");
    }

    @Test
    void evaluatorPromptsCarryTheirRubricAndTheFencedDocument() throws Exception {
        StubChatModel recording = new StubChatModel(MockEvaluatorModel::answer);
        service(recording, Duration.ofSeconds(10)).start("SUB-7", Samples.load("weak"));
        List<String> systems = recording.received().stream()
                .map(p -> p.getInstructions().getFirst().getText()).toList();
        assertThat(systems).anyMatch(s -> s.contains("DIMENSION=" + SkillsAndArchitectureNode.DIMENSION))
                .anyMatch(s -> s.contains("DIMENSION=" + ExperienceAndResilienceNode.DIMENSION))
                .anyMatch(s -> s.contains("DIMENSION=" + CultureAndGovernanceNode.DIMENSION))
                .anyMatch(s -> s.contains("TASK: red-flag-audit"));
        assertThat(recording.received()).hasSize(5);
        assertThat(StubChatModel.lastText(recording.received().getLast())).contains("<document>").contains("Sam Carter");
    }
}
