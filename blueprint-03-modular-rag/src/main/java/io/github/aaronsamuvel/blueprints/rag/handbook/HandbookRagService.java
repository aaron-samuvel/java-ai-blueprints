package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.core.task.TaskExecutor;

/**
 * The handbook's Blueprint 3 pipeline on Spring AI's {@link RetrievalAugmentationAdvisor}:
 * <ol>
 *   <li>pre-retrieval: {@link CompressionQueryTransformer} (folds chat history into a standalone question),
 *       {@link RewriteQueryTransformer} (search-friendly wording), {@link MultiQueryExpander} (3 variants);</li>
 *   <li>retrieval: {@link BranchingDocumentRetriever}, one search per variant, run in parallel on the
 *       given (virtual-thread) task executor;</li>
 *   <li>post-retrieval: {@link ConcatenationDocumentJoiner} then {@link DeduplicatingDocumentPostProcessor};</li>
 *   <li>generation: {@link ContextualQueryAugmenter} with {@code allowEmptyContext=false}, so with no
 *       context above the threshold the model is told to abstain.</li>
 * </ol>
 * The prompt templates carry a {@code TASK:} marker so the offline demo model can answer each step.
 */
public class HandbookRagService {

    static final PromptTemplate COMPRESS = new PromptTemplate("""
            TASK: compress-query
            Given the conversation history and a follow-up query, rewrite the follow-up as one standalone query
            that keeps every relevant detail. Reply with the query only.
            Conversation history:
            {history}
            Follow-up query: {query}
            Standalone query:""");

    static final PromptTemplate REWRITE = new PromptTemplate("""
            TASK: rewrite-query
            Rewrite the query so it retrieves the best results from {target}: drop filler words, keep the key terms.
            Reply with the rewritten query only.
            Original query: {query}
            Rewritten query:""");

    static final PromptTemplate EXPAND = new PromptTemplate("""
            TASK: expand-query
            Write {number} different versions of the query for a vector search, each on its own line, and nothing else.
            Original query: {query}
            Query variants:""");

    private final ChatClient chat;

    public HandbookRagService(ChatModel model, BranchingDocumentRetriever retriever, int maxDocuments, TaskExecutor executor) {
        ChatClient.Builder helper = ChatClient.builder(model);
        RetrievalAugmentationAdvisor advisor = RetrievalAugmentationAdvisor.builder()
                .queryTransformers(
                        CompressionQueryTransformer.builder().chatClientBuilder(helper).promptTemplate(COMPRESS).build(),
                        RewriteQueryTransformer.builder().chatClientBuilder(helper).promptTemplate(REWRITE)
                                .targetSearchSystem("the engineering knowledge base").build())
                .queryExpander(MultiQueryExpander.builder().chatClientBuilder(helper).promptTemplate(EXPAND)
                        .numberOfQueries(3).includeOriginal(false).build())
                .documentRetriever(retriever)
                .documentJoiner(new ConcatenationDocumentJoiner())
                .documentPostProcessors(new DeduplicatingDocumentPostProcessor(maxDocuments))
                .queryAugmenter(ContextualQueryAugmenter.builder().allowEmptyContext(false).build())
                .taskExecutor(executor)
                .build();
        this.chat = ChatClient.builder(model)
                .defaultSystem("Answer engineering questions in Markdown using only the provided context. "
                        + "Name the source file for each fact.")
                .defaultAdvisors(advisor)
                .build();
    }

    public GroundedAnswer ask(String question) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("question is required");
        ChatClientResponse response = chat.prompt().user(question).call().chatClientResponse();
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) response.context().getOrDefault(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT, List.of());
        String answer = response.chatResponse() == null ? "" : response.chatResponse().getResult().getOutput().getText();
        List<SourceCitation> citations = docs.stream().map(HandbookRagService::citation).toList();
        return new GroundedAnswer(answer, citations, docs.isEmpty());
    }

    static SourceCitation citation(Document d) {
        Object page = d.getMetadata().get("page");
        return new SourceCitation(String.valueOf(d.getMetadata().get("filename")), String.valueOf(d.getMetadata().get("section")),
                page instanceof Number n ? n.intValue() : null, String.valueOf(d.getMetadata().get("domain_category")),
                String.valueOf(d.getMetadata().get("effective_date")), Math.round(DeduplicatingDocumentPostProcessor.score(d) * 1000) / 1000.0);
    }
}
