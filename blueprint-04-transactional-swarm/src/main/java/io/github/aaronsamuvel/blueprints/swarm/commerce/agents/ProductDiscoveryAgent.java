package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.ProductRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.Product;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Hybrid product search: a vector similarity search over product descriptions and a SQL keyword
 * search, merged with reciprocal rank fusion (RRF, k = 60). Both legs are filtered by tenant.
 * The handbook uses Azure Cosmos DB DiskANN for the vector leg; here it is any Spring AI
 * {@link VectorStore} (SimpleVectorStore offline, pgvector in production).
 */
public class ProductDiscoveryAgent {

    public static final String NAME = "product-discovery";
    static final int RRF_K = 60;

    public record Recommendation(String sku, String name, String priceInr, int stock, double score) {}

    private final ChatClient chat;
    private final VectorStore vectors;
    private final ProductRepository products;

    public ProductDiscoveryAgent(ChatClient chat, VectorStore vectors, ProductRepository products) {
        this.chat = chat;
        this.vectors = vectors;
        this.products = products;
    }

    /** Index the whole catalogue (call at startup and after catalogue changes). */
    public void index() {
        vectors.add(products.findAll().stream().map(p -> Document.builder()
                .id(java.util.UUID.nameUUIDFromBytes((p.tenantId() + "/" + p.sku()).getBytes()).toString())
                .text(p.name() + ". " + p.description() + ". Category: " + p.category())
                .metadata(Map.of("tenant_id", p.tenantId(), "sku", p.sku()))
                .build()).toList());
    }

    public List<Recommendation> hybridSearch(String tenantId, String query, int limit) {
        List<String> vectorRanking = vectors.similaritySearch(SearchRequest.builder().query(query).topK(limit * 2)
                        .similarityThresholdAll()
                        .filterExpression(new FilterExpressionBuilder().eq("tenant_id", tenantId).build()).build())
                .stream().map(d -> (String) d.getMetadata().get("sku")).toList();
        Map<String, Integer> keywordHits = new HashMap<>();
        for (String word : Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).filter(w -> w.length() > 3).toList()) {
            for (Product p : products.keywordSearch(tenantId, "%" + word + "%")) keywordHits.merge(p.sku(), 1, Integer::sum);
        }
        List<String> keywordRanking = keywordHits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).map(Map.Entry::getKey).toList();
        Map<String, Double> fused = new LinkedHashMap<>();
        for (int i = 0; i < vectorRanking.size(); i++) fused.merge(vectorRanking.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        for (int i = 0; i < keywordRanking.size(); i++) fused.merge(keywordRanking.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        List<Recommendation> out = new ArrayList<>();
        fused.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(limit).forEach(e ->
                products.findByTenantIdAndSku(tenantId, e.getKey()).ifPresent(p -> out.add(new Recommendation(p.sku(), p.name(),
                        p.priceInr().toPlainString(), p.stock(), Math.round(e.getValue() * 10000) / 10000.0))));
        return out;
    }

    public AgentReply handle(Turn turn) {
        List<Recommendation> recs = hybridSearch(turn.key().tenantId(), turn.message(), 3);
        String catalogue = recs.stream().map(r -> r.sku() + " | " + r.name() + " | INR " + r.priceInr() + " | stock " + r.stock())
                .collect(Collectors.joining("\n"));
        String answer = chat.prompt()
                .system("TASK: recommend\nYou are the product discovery agent. Recommend only products from this list:\n" + catalogue)
                .messages(turn.history())
                .user(turn.message())
                .call().content();
        return new AgentReply(NAME, answer, Map.of("recommendations", recs));
    }
}
