package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

/**
 * Post-retrieval step: the three expanded queries often return the same chunk, so keep one copy per
 * document id (the best-scoring one), order by score and cap the context at {@code maxDocuments}.
 */
public class DeduplicatingDocumentPostProcessor implements DocumentPostProcessor {

    private final int maxDocuments;

    public DeduplicatingDocumentPostProcessor(int maxDocuments) {
        this.maxDocuments = maxDocuments;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        Map<String, Document> best = new LinkedHashMap<>();
        for (Document d : documents) {
            best.merge(d.getId(), d, (a, b) -> score(b) > score(a) ? b : a);
        }
        return best.values().stream()
                .sorted(Comparator.comparingDouble(DeduplicatingDocumentPostProcessor::score).reversed())
                .limit(maxDocuments)
                .toList();
    }

    static double score(Document d) {
        return d.getScore() == null ? 0 : d.getScore();
    }
}
