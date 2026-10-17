# Examples

## 1. Ask through HTTP (offline: stub model, in-memory store)
```bash
mvn -pl blueprint-03-modular-rag -am spring-boot:run
curl -s -X POST localhost:8083/api/ask -H 'Content-Type: application/json' \
     -d '{"question":"How much sick leave do I get?"}'
```
The response has `intent` (`POLICY`) and `sources` (it includes `leave-policy.md#Sick leave`), which
`ModularRagApplicationTest` checks. With the stub model, `answer` echoes the augmented prompt.

## 2. Compose the pipeline yourself
```java
VectorStore store = SimpleVectorStore.builder(new StubEmbeddingModel(256)).build();
new IngestionService(List.of(new MarkdownSectionParser()), store).ingestClasspathCorpus();
ModularRagService rag = new ModularRagService(chatModel, store, new KeywordQueryRouter(), 3, 0.2);
RagAnswer a = rag.ask("Kafka consumer lag alert, what should I check?");
```

## 3. Swap in pgvector + Ollama
See `src/it/java/.../PgVectorRagIT.java`. Build `OllamaEmbeddingModel` (`nomic-embed-text`, 768 dims)
and a `PgVectorStore`, then pass the store to the same `IngestionService` and `ModularRagService`. No other code changes.

## 4. Encode context as TOON
```java
String toon = ToonEncoder.encode(Map.of("context", List.of(
        Map.of("source", "refund-policy.md", "score", 0.82),
        Map.of("source", "leave-policy.md", "score", 0.41))));
int tokens = new TokenCounter(EncodingType.O200K_BASE).count(toon);
```
