package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Conditional, branching retriever: each (expanded) query is classified into a {@link DomainCategory}
 * and searched only in that partition, with a similarity floor (0.76 for real embeddings in the
 * handbook). A query that matches no category searches every partition. Each search is timed into
 * the Micrometer timer {@value #TIMER}, tagged with the category.
 */
public class BranchingDocumentRetriever implements DocumentRetriever {

    public static final String TIMER = "rag.vector.search";

    /** What one retrieval did; published to an optional listener for logs and tests. */
    public record RetrievalEvent(String query, String category, int hits, boolean virtualThread, String threadName) {}

    private final VectorStore vectorStore;
    private final int topK;
    private final double threshold;
    private final MeterRegistry meters;
    private final Consumer<RetrievalEvent> listener;

    public BranchingDocumentRetriever(VectorStore vectorStore, int topK, double threshold, MeterRegistry meters,
                                      Consumer<RetrievalEvent> listener) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.threshold = threshold;
        this.meters = meters;
        this.listener = listener == null ? e -> { } : listener;
    }

    public double threshold() {
        return threshold;
    }

    @Override
    public List<Document> retrieve(Query query) {
        Optional<DomainCategory> category = DomainCategory.classify(query.text());
        SearchRequest.Builder request = SearchRequest.builder().query(query.text()).topK(topK).similarityThreshold(threshold);
        category.ifPresent(c -> request.filterExpression(new FilterExpressionBuilder().eq("domain_category", c.name()).build()));
        String tag = category.map(Enum::name).orElse("ALL");
        List<Document> hits = Timer.builder(TIMER)
                .description("Latency of one vector similarity search in the handbook RAG pipeline")
                .tag("domain_category", tag)
                .register(meters)
                .record(() -> vectorStore.similaritySearch(request.build()));
        List<Document> result = hits == null ? List.of() : hits;
        listener.accept(new RetrievalEvent(query.text(), tag, result.size(), Thread.currentThread().isVirtual(), Thread.currentThread().getName()));
        return result;
    }
}
