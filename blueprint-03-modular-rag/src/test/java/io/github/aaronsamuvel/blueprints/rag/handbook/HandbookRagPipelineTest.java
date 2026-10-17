package io.github.aaronsamuvel.blueprints.rag.handbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.github.aaronsamuvel.blueprints.eval.EvalCase;
import io.github.aaronsamuvel.blueprints.eval.EvaluationGate;
import io.github.aaronsamuvel.blueprints.eval.LexicalJudgeChatModel;
import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** The full pipeline offline: stub embeddings, in-memory store, extractive demo model, lexical judge. */
class HandbookRagPipelineTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Queue<BranchingDocumentRetriever.RetrievalEvent> events = new ConcurrentLinkedQueue<>();
    private SimpleVectorStore store;
    private HandbookRagService rag;

    @BeforeEach
    void setUp() throws Exception {
        store = SimpleVectorStore.builder(new StubEmbeddingModel(256)).build();
        DocumentIngestionService ingestion = new DocumentIngestionService(store, HandbookDemoModel.create());
        new HandbookRagConfiguration().ingestHandbookCorpus(ingestion, true).run(new DefaultApplicationArguments());
        BranchingDocumentRetriever retriever = new BranchingDocumentRetriever(store, 3, 0.25, meters, events::add);
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("rag-test-");
        executor.setVirtualThreads(true);
        rag = new HandbookRagService(HandbookDemoModel.create(), retriever, 4, executor);
    }

    private static EvaluationGate gate() {
        return new EvaluationGate(new LexicalJudgeChatModel());
    }

    private static EvalCase evalCase(String question, GroundedAnswer answer, List<Document> context) {
        return new EvalCase(question, question, context.stream().map(Document::getText).toList(), answer.answerMarkdown());
    }

    private List<Document> contextFor(GroundedAnswer a) {
        return a.citations().stream()
                .flatMap(c -> store.similaritySearch(SearchRequest.builder().query(c.section()).topK(50).similarityThresholdAll().build()).stream()
                        .filter(d -> c.filename().equals(d.getMetadata().get("filename")) && c.section().equals(d.getMetadata().get("section"))))
                .toList();
    }

    @Test
    void runbookQuestionIsRoutedCitedAndPassesBothEvaluators() {
        String q = "What are the recovery steps for Kafka consumer lag on the ledger topic?";
        GroundedAnswer a = rag.ask(q);
        assertThat(a.abstainedDueToLowContext()).isFalse();
        assertThat(a.citations()).isNotEmpty().allMatch(c -> c.domainCategory().equals("RUNBOOKS"));
        assertThat(a.citations()).anyMatch(c -> c.filename().equals("kafka-consumer-lag-runbook.md") && c.effectiveDate().equals("2026-05-20"));
        assertThat(a.answerMarkdown()).containsIgnoringCase("consumer");
        EvaluationGate gate = gate();
        EvalCase c = evalCase(q, a, contextFor(a));
        assertThat(gate.relevancy(c).isPass()).as("RelevancyEvaluator").isTrue();
        assertThat(gate.facts(c).isPass()).as("FactCheckingEvaluator").isTrue();
    }

    @Test
    void securityAndArchitectureQuestionsUseTheirOwnPartitions() {
        GroundedAnswer security = rag.ask("How often must secrets be rotated under the security policy?");
        assertThat(security.citations()).isNotEmpty().allMatch(c -> c.domainCategory().equals("SECURITY_POLICY"));
        assertThat(security.answerMarkdown()).contains("90 days");

        GroundedAnswer architecture = rag.ask("Why does our architecture use the transactional outbox pattern for events?");
        assertThat(architecture.citations()).isNotEmpty().allMatch(c -> c.domainCategory().equals("ARCHITECTURE"));
        assertThat(architecture.citations()).anyMatch(c -> c.filename().equals("adr-007-outbox.md"));
    }

    @Test
    void abstainsWhenNothingClearsTheThreshold() {
        GroundedAnswer a = rag.ask("What is on the cafeteria menu this Friday?");
        assertThat(a.abstainedDueToLowContext()).isTrue();
        assertThat(a.citations()).isEmpty();
        assertThat(a.answerMarkdown()).isEqualTo(HandbookDemoModel.ABSTAIN);
    }

    @Test
    void threeExpandedQueriesAreSearchedInParallelOnVirtualThreadsAndDeduplicated() {
        GroundedAnswer a = rag.ask("What are the recovery steps for Kafka consumer lag on the ledger topic?");
        assertThat(events).hasSize(3).allMatch(BranchingDocumentRetriever.RetrievalEvent::virtualThread);
        assertThat(events).extracting(BranchingDocumentRetriever.RetrievalEvent::query).doesNotHaveDuplicates();
        int rawHits = events.stream().mapToInt(BranchingDocumentRetriever.RetrievalEvent::hits).sum();
        assertThat(a.citations().size()).isLessThan(rawHits);
        assertThat(a.citations()).extracting(c -> c.filename() + "#" + c.section()).doesNotHaveDuplicates();
    }

    @Test
    void vectorSearchLatencyIsRecordedPerPartition() {
        rag.ask("What are the recovery steps for Kafka consumer lag on the ledger topic?");
        var timer = meters.find(BranchingDocumentRetriever.TIMER).tag("domain_category", "RUNBOOKS").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(3);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isPositive();
    }

    @Test
    void enrichersAddSummaryAndKeywords() {
        List<Document> all = store.similaritySearch(SearchRequest.builder().query("policy").topK(100).similarityThresholdAll().build());
        assertThat(all).isNotEmpty().allSatisfy(d -> {
            assertThat(d.getMetadata()).containsKeys("excerpt_keywords", "section_summary", "content_hash", "effective_date", "domain_category");
        });
    }

    @Test
    void classifierRoutesByKeywords() {
        assertThat(DomainCategory.classify("restart the pod during an outage")).contains(DomainCategory.RUNBOOKS);
        assertThat(DomainCategory.classify("encryption policy for PII")).contains(DomainCategory.SECURITY_POLICY);
        assertThat(DomainCategory.classify("service boundary design")).contains(DomainCategory.ARCHITECTURE);
        assertThat(DomainCategory.classify("hello there")).isEmpty();
    }

    @SuppressWarnings("unused")
    private static ApplicationArguments args() {
        return new DefaultApplicationArguments();
    }
}
