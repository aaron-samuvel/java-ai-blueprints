package io.github.aaronsamuvel.blueprints.rag.routing;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Branching retriever: routes each query to a domain-filtered similarity search on the same
 * vector store (pgvector in production, SimpleVectorStore in tests). Conditional: SMALLTALK
 * retrieves nothing, and hits below the similarity threshold are dropped.
 */
public class RoutingDocumentRetriever implements DocumentRetriever {

    private final VectorStore vectorStore;
    private final QueryRouter router;
    private final int topK;
    private final double threshold;

    public RoutingDocumentRetriever(VectorStore vectorStore, QueryRouter router, int topK, double threshold) {
        this.vectorStore = vectorStore;
        this.router = router;
        this.topK = topK;
        this.threshold = threshold;
    }

    @Override
    public List<Document> retrieve(Query query) {
        QueryIntent intent = router.route(query.text());
        if (!intent.needsRetrieval()) return List.of();
        SearchRequest.Builder req = SearchRequest.builder()
                .query(query.text())
                .topK(topK)
                .similarityThreshold(threshold);
        if (intent.domain() != null) {
            req.filterExpression(new FilterExpressionBuilder().eq("domain", intent.domain()).build());
        }
        return vectorStore.similaritySearch(req.build());
    }
}
