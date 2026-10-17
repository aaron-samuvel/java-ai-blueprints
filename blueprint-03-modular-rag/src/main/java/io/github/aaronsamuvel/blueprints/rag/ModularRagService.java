package io.github.aaronsamuvel.blueprints.rag;

import java.util.List;

import io.github.aaronsamuvel.blueprints.rag.routing.QueryIntent;
import io.github.aaronsamuvel.blueprints.rag.routing.QueryRouter;
import io.github.aaronsamuvel.blueprints.rag.routing.RoutingDocumentRetriever;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Modular RAG built from Spring AI's composable pieces:
 * router (branch) to domain-filtered retriever to contextual augmenter to model.
 * Conditional behaviour:
 * <ul>
 *   <li>SMALLTALK skips retrieval entirely (plain chat, no advisor).</li>
 *   <li>No document above the threshold means the augmenter tells the model to say it doesn't know
 *       ({@code allowEmptyContext=false}).</li>
 * </ul>
 */
public class ModularRagService {

    private final QueryRouter router;
    private final ChatClient plainClient;
    private final ChatClient ragClient;

    public ModularRagService(ChatModel chatModel, VectorStore vectorStore, QueryRouter router,
                             int topK, double threshold) {
        this.router = router;
        this.plainClient = ChatClient.builder(chatModel)
                .defaultSystem("You are a friendly assistant. Keep replies short.")
                .build();
        this.ragClient = ChatClient.builder(chatModel)
                .defaultAdvisors(RetrievalAugmentationAdvisor.builder()
                        .documentRetriever(new RoutingDocumentRetriever(vectorStore, router, topK, threshold))
                        .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(false).build())
                        .build())
                .build();
    }

    public RagAnswer ask(String question) {
        QueryIntent intent = router.route(question);
        if (!intent.needsRetrieval()) {
            return new RagAnswer(plainClient.prompt().user(question).call().content(), intent, List.of());
        }
        ChatClientResponse response = ragClient.prompt().user(question).call().chatClientResponse();
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) response.context()
                .getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());
        List<String> sources = docs.stream()
                .map(d -> d.getMetadata().get("source") + "#" + d.getMetadata().get("section"))
                .distinct().toList();
        String answer = response.chatResponse() == null ? "" : response.chatResponse().getResult().getOutput().getText();
        return new RagAnswer(answer, intent, sources);
    }
}
