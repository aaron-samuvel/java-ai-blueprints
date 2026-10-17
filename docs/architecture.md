# Architecture

*(author's design, not from handbook; it follows the handbook's pillars)*

## Repository / module graph
```mermaid
flowchart TB
  root[java-ai-blueprints<br/>parent POM: Boot 4.0.8 + Spring AI 2.0.1 BOMs]
  root --> shared[shared<br/>StubChatModel · StubEmbeddingModel · AiAnswer]
  root --> p1[part-1-lifecycle]
  root --> b3[blueprint-03-modular-rag]
  root --> b5[blueprint-05-pii-tokenization-advisor]
  p1 --> shared
  b3 --> shared
  b5 --> shared
  root -.planned.-> b1[blueprint-01] & b2[blueprint-02] & b4[blueprint-04] & b6[blueprint-06] & b7[blueprint-07] & fin[pillar-finops-budget] & ev[pillar-eval-gates]
```

## Target runtime architecture (all blueprints)
```mermaid
flowchart LR
  client[Client / UI] --> api[Spring MVC controller<br/>virtual threads]
  api --> cc[ChatClient]
  subgraph chain[Advisor chain - order matters]
    direction LR
    pii[PII tokenisation<br/>B05] --> bud[FinOps budget<br/>reserve-call-commit] --> mem[ChatMemory<br/>tenant-partitioned B04] --> rag[RAG advisor<br/>B03] --> obs[Observability<br/>OpenInference]
  end
  cc --> chain --> model[(ChatModel<br/>Ollama / stub)]
  model -. tool calls .-> tools[Tools wrapped by<br/>@ToolPrivacyPolicy]
  rag --> vs[(pgvector)]
  mem --> pg[(Postgres)]
```

## Test layers (dual-layer verification)
```mermaid
flowchart LR
  u[Unit + slice tests<br/>StubChatModel, no network] -->|mvn verify| ci[default build]
  it[*IT tests<br/>Ollama / Testcontainers] -->|mvn -Pit verify| local[developer PC]
  ev[Evaluation gates<br/>Relevancy / FactChecking] -->|planned| local
```

## Key decisions
| Decision | Reason |
|---|---|
| Stub models in `shared/` | Deterministic tests that can prove what was or wasn't sent to a model |
| Advisors for cross-cutting AI concerns | One enforcement point; business code can't bypass it |
| PII advisor at `HIGHEST_PRECEDENCE` | Every later advisor, log and model sees only tokens |
| `*IT` under `-Pit` + `src/it/java` | Default build needs no Docker or Ollama; IT sources need the Ollama starter only in that profile |
| pgvector over Cosmos DB | Free, local, and one database for vectors and business data |
