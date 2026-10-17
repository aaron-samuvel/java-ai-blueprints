# Theory: J-AIDLC, the five pillars and the framework matrix

Source: handbook Part I, pages 1-4 (requirement-by-requirement status in the [traceability matrix](../docs/pdf-requirements-mapping.md)).
The stage and pillar names come from the handbook. The explanations are **author's design (not from handbook)**
unless stated otherwise.

## J-AIDLC: Java AI Development Lifecycle (7 stages)

```mermaid
flowchart LR
  S1[1 Domain & intent<br/>modelling with records] --> S2[2 Hermetic local<br/>prototyping, Dev Services]
  S2 --> S3[3 Context engineering<br/>RAG ETL + TOON]
  S3 --> S4[4 Dual-layer<br/>verification]
  S4 --> S5[5 Zero-trust guardrails<br/>+ FinOps]
  S5 --> S6[6 Semantic observability<br/>OpenInference]
  S6 --> S7[7 Production runtime<br/>virtual threads, GraalVM native]
  S7 -. feedback .-> S1
```

| # | Stage | What it means in practice | In this repo |
|---|---|---|---|
| 1 | Domain/intent modelling with records | Write the model's input and output as Java records first. The model has to fill a typed contract. | `IncidentTriage`, `StructuredOutputExample` |
| 2 | Hermetic local prototyping with Dev Services | Run local infrastructure (Ollama, Postgres) with Testcontainers or Arconia Dev Services. No cloud. | Substituted: `docker-compose.ai-support.yml` + env-configured services and the `-Pit` profile; stub models offline (no Docker on the build box, so no Arconia/Testcontainers) |
| 3 | Context engineering & RAG ETL with TOON | Ingest, chunk, embed and retrieve. Serialise context compactly (TOON, Token-Oriented Object Notation). | Blueprint 03 (`ToonEncoder`); json-io TOON in `pillar-eval-gates` |
| 4 | Dual-layer verification | Layer 1: deterministic tests with stub models. Layer 2: LLM-as-judge evaluators (relevancy, fact-checking). | stub tests in every module; `pillar-eval-gates` (FactChecking + Relevancy + 3 author's gates) |
| 5 | Zero-trust guardrails with FinOps | Advisors that tokenise PII, enforce tool policies and reserve a token budget before each call. | Blueprint 05; `pillar-finops-budget` |
| 6 | Semantic observability with OpenInference | Trace prompts, tools and tokens with OpenInference semantic conventions on OpenTelemetry. | `observability/` here: Spring AI observations + `openinference.span.kind`; OTLP export not run |
| 7 | Production runtime with virtual threads & GraalVM native | Use virtual threads for blocking LLM I/O, and native images for fast start-up. | `VirtualThreadFanOut`, `spring.threads.virtual.enabled`; GraalVM native **not done** (no GraalVM on the box) |

> **TOON caveat:** the handbook says TOON saves 40–50% of tokens. Blueprint 03 measured 38–39% against compact
> JSON on uniform tables, 16% on long-text chunks, and 26–31% *more* tokens on nested data
> (see [Blueprint 03 theory](../blueprint-03-modular-rag/theory.md#toon-measured)).

## Five pillars

| Pillar | Why Java is good at it | Example |
|---|---|---|
| Virtual threads | LLM calls block for seconds. Virtual threads make thread-per-request cheap, with no reactive code needed. | `VirtualThreadFanOut` |
| Records as LLM contracts | Immutable, validated, and they map to a JSON schema for structured output | `IncidentTriage` |
| Advisor API | Spring AI's interceptor chain around every model call: guardrails, memory, RAG, audit | `AuditTrailAdvisor`, `PrivacyProtectionCallAdvisor`, `FinOpsBudgetAdvisor` |
| Testcontainers / Arconia | Real local Ollama and pgvector in tests and dev, started on demand | `docker-compose.ai-support.yml` + `-Pit` ITs (substitute for Arconia/Testcontainers) |
| Unified data & transactions | Vectors, chat memory and business rows in one Postgres, under one transaction | Blueprint 04 (stock + order + memory in one transaction), Blueprint 07 (tenant-scoped SQL) |

## Framework matrix

The rows are the handbook's five frameworks. The "best for" column is **author's design (not from handbook)**.

| Framework | Style | Best for | Used in |
|---|---|---|---|
| Spring AI 2.0 | Spring-native `ChatClient`, advisors, vector stores, MCP | Most enterprise apps on Spring Boot | all modules |
| LangChain4j | Framework-agnostic AI services, many integrations | Non-Spring or Quarkus apps | — |
| Embabel | Goal-oriented action planning (GOAP) agents on the JVM | Planning agents | Blueprint 01 |
| LangGraph4j | Stateful graphs, checkpoints, human-in-the-loop | Multi-step and HITL workflows | Blueprint 02 |
| Spring AI Alibaba | Alibaba extensions, graph and JManus agent | Alibaba Cloud / Nacos users | catalogue only (Nacos replaced, see version notes) |
