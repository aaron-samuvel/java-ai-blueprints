# Handbook traceability matrix

This matrix checks this repository against both handbook PDFs, requirement by requirement:
- the master handbook, *Enterprise Java & Spring Boot AI Systems* (18 pages, Parts I-IV);
- the companion runbook, *Pre-Installation & Supporting Environment Flow Guide* (10 pages).

Page numbers are the PDF page footers. "B1" to "B7" are the Part III blueprints.

**Status key**
- **Done**: implemented and covered by an offline test (or, for documents, written and checked).
- **Partial**: implemented, but part of the requirement is missing or was not run here. The cell says which part.
- **Substituted**: an equivalent replaces the named technology. The cell gives the reason.
- **Not done**: absent. The cell gives the reason.

Nothing here is marked Done on the strength of a run that did not happen. Live (`-Pit`) runs are named
explicitly where they took place.

**Counts (119 requirements):** Done 75 · Partial 23 · Substituted 16 · Not done 5

Most Partial and Substituted rows have the same three causes:
- there is no Docker on the build box, so no Testcontainers, Arconia, Presidio, Nacos or LGTM;
- the repository is pinned to Spring Boot 4.0.8 and Spring AI 2.0.1, which Embabel 1.5.3 and Spring AI Alibaba don't support;
- paid cloud services (Azure, OpenAI, Anthropic) are out of scope.

Module READMEs have the detailed per-module tables. [environment.md](environment.md) covers the companion guide step by step.

## Part I: J-AIDLC, five pillars, framework matrix (master handbook pp. 2-4)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| I 1.1, p.2 | Pillar 1: virtual threads (`spring.threads.virtual.enabled=true`) for fan-out | `part-1-lifecycle` `VirtualThreadFanOut`; every app's `application.properties`; B02 parallel nodes, B07 `ParallelAgent` | **Done** |
| I 1.1, p.2 | Pillar 2: records with `@JsonClassDescription` / `@JsonPropertyDescription` as LLM contracts | `IncidentTriage` (part-1), B01 incident records, B02 submission records, B03 `SourceCitation` | **Done** |
| I 1.1, p.2 | Pillar 3: Advisor API for RAG, memory, PII, guardrails, FinOps | `AuditTrailAdvisor`, `PrivacyProtectionCallAdvisor` (B05), `FinOpsBudgetAdvisor`, `RetrievalAugmentationAdvisor` (B03) | **Done** |
| I 1.1, p.2 | Pillar 4: Dev Services / Testcontainers start Ollama, PGVector and LGTM automatically | `docker-compose.ai-support.yml` + env-configured `-Pit` ITs | **Substituted**: there is no Docker on the build box, so Arconia and Testcontainers cannot run. Started by hand from the compose file instead |
| I 1.1, p.2 | Pillar 5: hybrid vector search and ACID writes on one data plane, plus method-level RBAC | B04 (`ProductDiscoveryAgent` RRF + `@Transactional` order and refund tools); B05/B06 Spring Security roles | **Partial**: the vectors are in `SimpleVectorStore`, not in the same Postgres as the rows |
| I 1.2, p.3 | Stage 1: domain and intent modelling with records (GOAP pre/post-conditions) | part-1 `IncidentTriage`; B01 `platform/` type-driven preconditions | **Done** |
| I 1.2, p.3 | Stage 2: hermetic local prototyping with Arconia Ollama Dev Services or Docker Model Runner | stub models by default, `BLUEPRINTS_MODEL=ollama` under `-Pit` | **Substituted**: no Docker or Arconia; offline stub models give the same "no cloud key" property |
| I 1.2, p.3 | Stage 3: Docling or Tika ingestion, summary and keyword enrichment, `RetrievalAugmentationAdvisor` | B03 `DocumentIngestionService` (Tika/PDFBox), `SummaryMetadataEnricher`, `KeywordMetadataEnricher` | **Done**: Tika is one of the two options named |
| I 1.2, p.3 | Stage 3: json-io TOON cuts tokens by 40-50% | `pillar-eval-gates` `ToonContextSerializer` (json-io 4.117.0); B03 `ToonEncoder` | **Partial**: implemented and measured. Flat tables saved 32% (json-io) and 38-39% (B03) against compact JSON; nested objects saved nothing (json-io +1.8%, B03 +26-31%). The 40-50% figure holds only against pretty-printed JSON |
| I 1.2, p.3 | Stage 4: deterministic mock layer (FakeOperationContext) plus `RelevancyEvaluator` / `FactCheckingEvaluator` gates | stub-model tests in every module; B01 `LlmInvocation` records; `pillar-eval-gates` | **Done**: FakeOperationContext itself is Embabel's (see B01 substitution) |
| I 1.2, p.3 | Stage 5: PII tokenisation and token-budget advisors on every ChatClient, failing closed | B05 advisor (fails closed without a session); `FinOpsBudgetAdvisor` (throws before the call) | **Partial**: B05 `PrivacyChatService` now chains both (PII, then FinOps). The other demos do not: the PII advisor lives inside the B05 app module |
| I 1.2, p.4 | Stage 6: Micrometer/OTel with OpenInference conventions, exported to Phoenix or Tempo | part-1 `observability/` (`OpenInferenceObservationFilter`, `ObservabilityTest`); B02 node observations; B03 `rag.vector.search` timer | **Partial**: observations and span kinds are tested in-process; OTLP export to LGTM or Phoenix was not run |
| I 1.2, p.4 | Stage 7: virtual threads, HTTP timeouts and circuit breakers | virtual threads everywhere; B06 switchboard timeout fallback | **Partial**: timeouts yes; no circuit-breaker library |
| I 1.2, p.4 | Stage 7: GraalVM native images for MCP servers and edge agents | — | **Not done**: skipped to keep scope to quick wins (user's call); there is no GraalVM on the build box |
| I 1.3, p.4 | Framework matrix: Spring AI 2.0 (`ChatClient`, advisors, vector stores, MCP, `@Tool`/`@McpTool`) | all modules (Spring AI 2.0.1, Boot 4.0.8) | **Done**: `@Tool` is used; `@McpTool` is not (see B06) |
| I 1.3, p.4 | Framework matrix: LangChain4j `@AiService` | — | **Not done**: skipped to keep scope to quick wins (user's call); Spring AI covers every blueprint, and LangChain4j on Boot 4.0.8 was not attempted |
| I 1.3, p.4 | Framework matrix: Embabel `@Agent` / `@Action` / `@Goal` / `@Condition` | B01 `platform/` (own annotations with the same names) | **Substituted**: Embabel 1.5.3 needs Spring Boot 4.1.1; this repository is pinned to 4.0.8 |
| I 1.3, p.4 | Framework matrix: LangGraph4j `StateGraph`, reducers, checkpoints, HITL, hooks | B02 | **Done** |
| I 1.3, p.4 | Framework matrix: Spring AI Alibaba (ReactAgent, Nacos A2A, Sequential/Parallel/LoopAgent) | B07 `workflow/`, `mesh/` on plain Spring AI | **Substituted**: Spring AI Alibaba is not on Spring AI 2.0.1; the patterns are built by hand |

## Part II: GitHub gems (pp. 4-5)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| II table, pp.4-5 | 10 rows naming 15 repositories, each with a link | `part-2-github-gems/gems.csv` (1a-10b), README table | **Done**: all 15 returned HTTP 200 from the GitHub API on 17 Oct 2026 (`GitHubCatalogueIT`, `-Pit`, on the build box and again on the owner's PC) |
| II table, pp.4-5 | Each gem's innovation is mapped to a blueprint | `gems.csv` "related" column, `theory.md` mapping | **Done**: the mapping follows the blueprint whose text cites the repository |
| II table, p.5 | Owner of `springboot-ai-rag-docingest` (the table gives none) | `anmiralles/springboot-ai-rag-docingest` | **Done**: the owner is taken from Blueprint 3's reference line (p.9) |

## Part III, Blueprint 1: GOAP FinOps & incident remediation agent (pp. 6-8)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B1, p.7 | Records `AnomalyAlert`, `TelemetrySnapshot`, `RootCauseHypothesis`, `RemediationPlan` with Jackson descriptions | `blueprint-01.../incident/*.java` | **Done** |
| B1, p.7 | `@Agent CloudRemediationAgent`: parseAlert (temperature 0), collectTelemetry (no LLM), diagnoseRootCause (tool group), `@Condition` ≥ 0.80, deep trace, `@AchievesGoal` | `incident/CloudRemediationAgent.java` | **Done** |
| B1, p.7 | Embabel framework (`embabel-agent-starter`, test support) | B01 `platform/` | **Substituted**: Embabel 1.5.3 needs Boot 4.1.1. `LlmInvocation` plays the role of `FakeOperationContext` |
| B1, p.7 | `PrometheusMetricsClient`, `LokiLogClient` | interfaces + `InMemoryTelemetry` | **Partial**: no live Prometheus or Loki client |
| B1, p.7 | Model mixing: a fast local model and a frontier model | `platform/LlmOptions` roles; `GOAP_FAST_MODEL` / `GOAP_FRONTIER_MODEL` | **Done**: offline stubs; no frontier model was called |
| B1, p.7 | `POST /api/v1/incidents/investigate` and the Spring Shell `execute "..." -p -r` | `IncidentInvestigationController`, `IncidentShellCommands` (`shell` profile) | **Done** |
| B1, p.7 | Tests: exact prompt contents, zero-LLM telemetry step, low-confidence path | `CloudRemediationAgentTest` (8), `IncidentInvestigationApiTest` (2) | **Done** |

## Part III, Blueprint 2: LangGraph4j stateful graph & HITL (pp. 8-9)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B2, p.8 | `ReviewWorkflowState` with reducer and appender channels | `submission/ReviewWorkflowState.java` | **Done** |
| B2, p.8 | 4 parallel evaluator nodes on virtual threads | `addParallelNodeExecutor`; 4 evaluator nodes | **Done** |
| B2, p.8 | Consensus aggregator with 3 exits (≥ 85 auto-approve, auto-reject, variance > 25 → human escalation) | `ConsensusAggregatorNode`, `ConsensusPolicy` | **Done** |
| B2, p.8 | `interruptBefore` the human node, `updateState`, resume | `SubmissionReviewGraph`, `SubmissionWorkflowService` | **Done** |
| B2, p.8 | MemorySaver for dev, JDBC/PostgreSQL saver for prod | `JdbcCheckpointSaver` (`blueprints.hitl.checkpoint=jdbc`) | **Partial**: tested on H2 only; PostgreSQL not run |
| B2, p.8 | Node hooks for tracing spans and timeouts | `NodeExecutionHooks` | **Done** |
| B2, p.8 | REST: start, state, resume | `SubmissionWorkflowController` | **Done** |
| B2, p.9 | Provider switch `llm.provider=mock / ollama / openai` | `MockEvaluatorModel`; app `ChatModel` | **Partial**: Ollama only under `-Pit` (not run for B2); OpenAI not wired (paid key) |
| B2, p.9 | Tests for all 3 branches and pause/resume; batch smoke script | `SubmissionReviewGraphTest`, `JdbcCheckpointSaverTest`, `SubmissionWorkflowApiTest`, `ConsensusPolicyTest`; `scripts/smoke-test-workflows.sh` | **Done** |

## Part III, Blueprint 3: modular, branching, conditional RAG (pp. 9-10)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B3, p.9 | `DocumentIngestionService` for PDF, Markdown and Office through Docling | `handbook/DocumentIngestionService` (PDFBox page reader, Tika, Markdown) | **Substituted**: Docling needs a separate Python service, so Tika/PDFBox is used instead (Stage 3 names Tika as an option) |
| B3, p.9 | Metadata `domain_category`, `effective_date`, summary and keyword enrichers | same service, Spring AI enrichers | **Done** |
| B3, p.9 | `POST /api/v1/rag/ingest`, idempotent by content hash | `NEW` / `UNCHANGED` / `REPLACED` | **Done** |
| B3, p.9 | Pre-retrieval compression, rewrite and 3-way multi-query in parallel | `HandbookRagService` | **Done** |
| B3, p.9 | `BranchingDocumentRetriever` over 3 domains, threshold ≥ 0.76 | `DomainCategory` filter; 0.76 with real embeddings, 0.25 with stubs | **Done**: the threshold is lower offline because the stub embeddings score lower |
| B3, p.9 | Post-retrieval de-duplication | `DeduplicatingDocumentPostProcessor` | **Done** |
| B3, p.10 | `GroundedAnswer` with citations and abstention | `POST /api/v1/rag/query` | **Done** |
| B3, p.10 | Tests with `RelevancyEvaluator` / `FactCheckingEvaluator` | `HandbookRagPipelineTest` | **Done**: offline lexical judge |
| B3, p.10 | Micrometer vector-search latency; `aiEnvironment` health | `rag.vector.search` timer; `/actuator/health/aiEnvironment` | **Done** |
| B3, p.10 | PGVector + Ollama through Arconia Dev Services / Testcontainers | `PgVectorRagIT` against env-configured pgvector | **Substituted**: no Docker on the build box |

## Part III, Blueprint 4: transactional multi-agent swarm (pp. 11-12)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B4, p.11 | `AgentOrchestratorService` with dynamic handoffs | `commerce/AgentOrchestratorService` | **Done** |
| B4, p.11 | `TriageRouterAgent` (routing, tenant and session state) | `agents/TriageRouterAgent` | **Done** |
| B4, p.11 | `ProductDiscoveryAgent` hybrid vector + keyword search | `agents/ProductDiscoveryAgent` (RRF k=60) | **Done** |
| B4, p.11 | `OrderExecutionAgent` `@Tool`s over `@Transactional` Spring Data | `agents/OrderTools`, `domain/CommerceRepositories` | **Done**: Spring Data JDBC rather than JPA |
| B4, p.11 | `RefundAndDisputeAgent` with ownership check and evaluator-optimizer loop | `agents/RefundAndDisputeAgent`, `RefundPolicy` | **Done** |
| B4, p.11 | `TenantPartitionedChatMemory` with `TenantSessionKey` and sliding-window compaction | `memory/TenantPartitionedChatMemory` | **Done** |
| B4, p.11 | `POST /api/v1/swarm/chat`, SSE, session history; live handoff badges | `web/SwarmChatController`, `static/swarm.html` | **Done** |
| B4, p.11 | Cosmos DB DiskANN or PostgreSQL + PGVector | `SimpleVectorStore` | **Substituted**: no Azure account (paid), offline-first |
| B4, p.12 | Integration tests | `CommerceSwarmApiTest` (11), `TenantPartitionedChatMemoryTest` (2) | **Done**: offline |

## Part III, Blueprint 5: zero-trust PII tokenisation (pp. 12-13)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B5, p.12 | Pluggable `PiiAnalyzerSpi`; regex recognisers (email, phone, card, SSN/PAN, IBAN) | `detect/RegexPiiAnalyzer`, `CompositePiiAnalyzer` | **Done** |
| B5, p.12 | JVM-local OpenNLP NER (person, location) | `detect/OpenNlpPiiAnalyzer` | **Partial**: dictionary NER only; the statistical `en-ner-*.bin` models are not bundled or run |
| B5, p.12 | Optional Microsoft Presidio | `detect/PresidioPiiAnalyzer` (fails closed) | **Partial**: tested against a stub HTTP server, not a real container |
| B5, p.12 | Request-scoped vault with deterministic surrogates | `vault/PrivacyTokenVault` (`<PII:EMAIL_1>`) | **Done**: token syntax differs from the example `[[PII_PERSON_1]]` |
| B5, p.12 | Response policies DETOKENIZE / REDACT / BLOCK_ON_LEAK | `vault/ResponsePolicy` | **Done** |
| B5, p.12 | `PrivacyProtectionCallAdvisor` covering user, system, RAG and tool messages | `advisor/PrivacyProtectionCallAdvisor` | **Done** |
| B5, p.12 | ChatMemory boundary | advisor ordering | **Partial**: there is no memory advisor in the demo pipeline |
| B5, p.13 | `@ToolPrivacyPolicy`, least-privilege tool exposure, selective restore, re-tokenised output | `tools/` | **Done** |
| B5, p.13 | Same protection for MCP tools | `ToolPolicyRegistry.register` | **Partial**: tested with a stand-in callback; no live MCP server |
| B5, p.13 | `POST /api/v1/privacy/inspect-roundtrip` with a deterministic ChatModel and audit | `inspect/` | **Done**: a real jar run is captured in the module README |
| B5, p.13 | Spring Security integration | `Authentication` / `SecurityContext` | **Partial**: no HTTP filter chain in the demo |

## Part III, Blueprint 6: MCP server mesh & model switchboard (pp. 13-14)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B6, p.13 | MCP server over Streamable HTTP, secured by OAuth2 JWT | `application.properties`, `security/SecurityConfiguration` | **Done** |
| B6, p.13 | SSE transport too | `spring.ai.mcp.server.protocol=SSE` | **Partial**: one protocol per node; SSE is not tested |
| B6, p.13 | `BatchIntrospectionMcpTools` with a guarded restart | `ops/BatchIntrospectionMcpTools` (Spring Batch 6) | **Done** |
| B6, p.13 | `JvmDiagnosticsMcpTools`: heap, GC, virtual-thread pinning (JFR), health | `ops/JvmDiagnosticsMcpTools`, `JfrEventMonitor` | **Done** |
| B6, p.13 | `DynamicOpenApiMcpBridge` with tool-list change notifications | `ops/DynamicOpenApiMcpBridge` | **Done**: GET operations only |
| B6, p.13 | `@McpTool` annotations | Spring AI `@Tool` | **Substituted**: `@Tool` is one of the two options the handbook lists |
| B6, p.13 | MCP resources and prompt templates | `ops/OpsMcpResourcesAndPrompts` (`@McpResource` ops://batch/jobs, ops://batch/executions/{executionId}, ops://jvm/heap; `@McpPrompt` diagnose-failed-batch-job, jvm-health-review) | **Done**: `OpsNodeTest` lists and reads them through a real MCP client (commit 424f6de) |
| B6, p.14 | Separate `mcp-ops-server` and `ai-switchboard-host` modules | one module with `MCP_TOOLSET=ops` and `host/` | **Substituted**: one deployable plays both roles, which keeps the build small |
| B6, p.14 | `ModelSwitchboardRouter` by complexity, privacy and latency | `host/ModelSwitchboardRouter` | **Partial**: stub models only; no live Ollama or hosted model (paid keys) |
| B6, p.14 | `StructuredOutputValidationAdvisor` with retry | `ModelSwitchboardRouter.extract` | **Done** |
| B6, p.14 | Tests: discovery, OAuth2 rejection, cloud-timeout fallback | `SecuredNodeTest`, `OpsNodeTest`, `ModelSwitchboardRouterTest` | **Done** |
| B6, p.13 | Docker MCP Catalog servers | — | **Not done**: skipped (user's call): needs Docker Desktop on the user's PC |

## Part III, Blueprint 7: A2A mesh & self-healing NL2SQL (pp. 14-15)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| B7, p.14 | Sequential, Parallel and Loop agents with compaction and loop guard | `workflow/` | **Done** |
| B7, p.14 | `SchemaVectorIndexer` (top-K tables into the prompt) | `dataagent/SchemaVectorIndexer` | **Done**: offline embeddings |
| B7, p.14 | `SqlAstGuardrailValidator` (JSqlParser, SELECT-only, tenant filter, LIMIT ≤ 500) | `dataagent/SqlAstGuardrailValidator` | **Done** |
| B7, p.15 | `SelfHealingSqlLoopAgent` (≤ 3 retries, EXPLAIN, read-only) | `dataagent/SelfHealingSqlLoopAgent` | **Done** |
| B7, p.15 | `ExecutiveInsightSynthesizer` → `DataAnalysisReport` + `ChartSpecification` | `dataagent/ExecutiveInsightSynthesizer` | **Done** |
| B7, p.14 | Agent cards in Nacos / Spring Cloud Discovery | `mesh/LocalAgentRegistry` behind `AgentRegistry` | **Substituted**: no Nacos client in the build; Nacos is in the compose file |
| B7, p.14 | A2A protocol | `mesh/A2aJsonRpcController` (`message/send`, agent card) | **Partial**: no streaming, `tasks/get` or push notifications |
| B7, p.15 | JManus-style coordination | `mesh/MeshCoordinator` | **Done** |
| B7, p.15 | Testcontainers PostgreSQL tests (multi-join, typo self-heal, DROP blocked) | `DataAgentApiTest` on H2 | **Partial**: the scenarios are tested, but not on PostgreSQL (no Docker) |
| B7, p.14 | Spring AI Alibaba | — | **Substituted**: built on plain Spring AI 2.0.1 |

## Part IV: FinOps authority, evaluation gates, constitution (pp. 15-18)

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| IV 10.1, p.16 | High-priority `CallAdvisor` doing Reserve → Call → Commit/Release | `pillar-finops-budget` `FinOpsBudgetAdvisor` | **Done** |
| IV 10.1, p.16 | Atomic reservation in Redis/PostgreSQL, daily/monthly budget, `BudgetExceededException` before the HTTP call | `JdbcTokenBudgetLedger` (conditional `UPDATE`, daily periods), `RedisTokenBudgetLedger` (Lua), `TokenBudgetLedger` | **Done**: tested on H2 and on an in-memory Redis fake (32 concurrent reservations, exactly 10 granted). Live Redis: `RedisTokenBudgetLedgerIT` passed on the owner's PC on 17 Oct 2026 against the `ai-redis` container (32 concurrent reservations of 100 against 1,000, exactly 10 granted, 1,000 reserved). Not run on a live PostgreSQL |
| IV 10.1, p.16 | Commit exact `Usage` from `ChatResponse` metadata; release on failure | `FinOpsBudgetAdvisor` | **Done** |
| IV 10.1, p.16 | Cost in micro-dollars | `PriceList` (INR per 1K tokens) | **Substituted**: the user is in India |
| IV 10.1, p.16 | Redis store or a Cycles authority service | `redis/RedisTokenBudgetLedger` + `SpringDataRedisBudgetGateway` (Lua reserve/settle, reservation TTL) behind `TokenBudgetAuthority` | **Done**: Redis, not Cycles. Offline test on a fake gateway; the Lua scripts passed in `RedisTokenBudgetLedgerIT` (`-Pit`) against a real Redis on the owner's PC (17 Oct 2026, 2 of 2 tests) |
| IV 10.2, p.16 | Groundedness gate (`FactCheckingEvaluator`) | `pillar-eval-gates` `EvaluationGate`, `FiveGateSuiteTest` | **Done** |
| IV 10.2, p.16 | Relevance gate (`RelevancyEvaluator`) | same | **Done** |
| IV 10.2, p.16 | Judge model through Testcontainers Ollama in CI | `LexicalJudgeChatModel` in CI; `OllamaJudgeIT` under `-Pit` | **Substituted**: no Docker in CI or on the box, so a deterministic judge is used |
| Cover, p.1 | "5 LLM evaluator gates" (the body names only 2) | `gates/StructuredOutputEvaluator`, `PiiLeakEvaluator`, `AnswerTokenBudgetEvaluator`, `GateSuite` | **Substituted**: gates 3-5 are author's design, because the handbook does not name them |
| IV 10.2, p.16 | json-io TOON serialisation | `toon/ToonContextSerializer` | **Partial**: measured savings were lower than the handbook's 40-50% (see Stage 3); cyclic references not tested |
| IV 10.3, pp.16-17 | `AGENTS.md` and `CLAUDE.md` at the repository root | `AGENTS.md` (the 4 handbook sections plus repo rules), `CLAUDE.md` (imports it) | **Done** |
| IV 10.3, p.17 | Constitution rule: every ChatClient wires the PII and FinOps advisors | see Stage 5 | **Partial**: only B05 wires both; the other demo modules are not compliant yet |
| IV 10.4, pp.17-18 | Summary matrix of blueprints and reference repositories | README module table; `gems.csv` | **Done** |

## Companion guide: Pre-Installation & Supporting Environment Flow (10 pp.); details in environment.md

| PDF section / page | Requirement | Repo location | Status |
|---|---|---|---|
| Guide I 1.1, p.2 | Layer 1: JDK 21, Maven 3.9+ | `~/tools` on the box; `scripts/preflight-check.*` | **Done** |
| Guide I 1.1, p.2 | Layer 1: SDKMAN!, GraalVM, Gradle 9, jbang | — | **Not done**: not needed by any module; nothing installed beyond the JDK and Maven |
| Guide I 1.1, p.2 | Layer 1: uv/uvx, Node/npx | checked by the preflight script | **Done**: present on the build box |
| Guide I 1.2, p.2 | Layer 2: Docker Desktop > 4.43.2, Model Runner, MCP Catalog | version check in the preflight script | **Not done**: no Docker on the box; Desktop extensions are a GUI install on the user's PC |
| Guide I 1.2, p.2 | Layer 2: Podman alternative | `docs/environment.md` | **Partial**: documented only, not run |
| Guide I 1.3, p.3 | Layer 3: Ollama and 4 pre-pulled models | compose `ollama` service; `preflight-check.sh --pull` | **Partial**: pulling is opt-in; llava is not used |
| Guide I 1.4, p.3 | Hardware tiers | `--tier1`; `docs/setup.md` | **Done** |
| Guide II 2.1, pp.3-4 | Compose: pgvector pg17, Presidio, Nacos 2.4.2, Redis Stack, otel-lgtm | `docker-compose.ai-support.yml` | **Done**: the stack itself was not started here (no Docker) |
| Guide II, p.4 | `CREATE EXTENSION vector / hstore / uuid-ossp` | `infra/postgres/init.sql`; preflight | **Done** |
| Guide II 2.2, p.4 | Variables matrix (OpenAI, Anthropic, Mistral, DashScope, Pinecone/Stock, Azure) | `.env.template` | **Done**: placeholders only; no secrets committed |
| Guide III, pp.5-8 | Per-blueprint pre-installation flows (reference repositories) | `docs/environment.md`, Part III table | **Partial**: this repository implements its own blueprints; the reference repositories were not cloned or run |
| Guide III B4, p.6 | `az login`, `azd up` (Route A) | `Dockerfile` + `docker-compose.apps.yml` (all seven apps, ports 8081-8087), `docs/deployment.md` | **Substituted**: free local deployment instead of Azure (paid; azd documented as optional). Verified on the owner's PC on 17 Oct 2026: all seven images built, all seven containers started and answered their README requests (B01 on host port 18081 via `B01_HOST_PORT`, because 8081 was taken) |
| Guide IV 1, p.8 | `scripts/preflight-check.sh` (versions, Ollama, ports, pgvector) | `scripts/preflight-check.sh`, `.ps1` | **Done**: run on the box: 0 failed, 8 warnings |
| Guide IV 2, p.8 | `docker-compose.ai-support.yml` + `.env.template`, healthchecks, named volumes | same | **Partial**: Nacos and LGTM have no compose healthcheck; the preflight script checks them |
| Guide IV 3, p.8 | `SupportingEnvironmentHealthIndicator` at `/actuator/health/aiEnvironment` with a console table | `shared/.../env/` | **Done** |
| Guide IV 4.1, p.9 | Port and service matrix | `.env.template` ports, preflight | **Done** |
