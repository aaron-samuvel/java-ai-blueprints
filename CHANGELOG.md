# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added
- `blueprint-03-modular-rag/` (M2): keyword query router with SMALLTALK, POLICY, TECHNICAL and GENERAL branches;
  domain-filtered `RoutingDocumentRetriever`; `RetrievalAugmentationAdvisor` with
  `ContextualQueryAugmenter(allowEmptyContext=false)`; Markdown section parser; Docling stub parser;
  classpath corpus ETL; `POST /api/ask`; pgvector + Ollama IT under `-Pit`.
- TOON subset encoder and `ToonVsJsonTokenMeasurementTest` (JTokkit cl100k/o200k). Measured: 38–39% fewer tokens
  than compact JSON on uniform tables, 16% on long-text chunks, 26–31% more on nested data.

- `blueprint-07-a2a-nl2sql-agent/` (M3): JSqlParser 5.4 `SqlGuard` (single SELECT, table allow-list, LIMIT 500
  added or capped), self-healing `Nl2SqlAgent` (1 + 3 attempts, error feedback), A2A-style agent card and task
  endpoints, H2 schema with synthetic data.
- `pillar-finops-budget/` (M3): `TokenBudgetLedger` (per-tenant reserve, commit, release; thread-safe),
  `FinOpsBudgetAdvisor`, `TokenEstimator` (JTokkit + margin), `PriceList` (INR per 1K tokens), `FinOpsDemo`.

- `pillar-eval-gates/` (M4): `EvaluationGate` over Spring AI `RelevancyEvaluator` + `FactCheckingEvaluator`,
  deterministic `LexicalJudgeChatModel`, golden and regression sets, Ollama judge IT under `-Pit`.
- `blueprint-02-langgraph4j-hitl-evaluation/` (M4): LangGraph4j 1.9.5 drafter, evaluator and human-review graph with
  `MemorySaver` checkpoints, `interruptBefore` and resume, and a REST review API.
- `blueprint-04-transactional-swarm/` (M4): triage, refund and notify agents in one `@Transactional` boundary;
  tenant-partitioned `ChatMemoryRepository` on H2; outbox table; rollback and isolation tests.

### Changed
- Docs: README, mapping, architecture and progress updated for M4.
- Docs: README, mapping, version notes (TOON claim now measured), architecture and progress updated for M2.
- Docs: README, mapping, architecture and progress updated for M3.

## [0.1.0] - 2026-10-17 (foundation milestone, not yet published)

### Added
- Maven multi-module root on Java 21, Spring Boot 4.0.8 and Spring AI 2.0.1. Surefire runs the
  offline tests by default; failsafe runs the live Ollama tests under the `it` profile.
- `shared/`: deterministic `StubChatModel`, `StubEmbeddingModel`, `AiAnswer` record, `StubModelConfiguration`.
- `part-1-lifecycle/`: J-AIDLC theory, record output contract, `AuditTrailAdvisor`, virtual-thread fan-out.
- `blueprint-05-pii-tokenization-advisor/`: `PiiTokenizationAdvisor`, `TokenVault`,
  `@ToolPrivacyPolicy`, `PrivacyEnforcingToolCallback`, REST endpoint, tests and a live IT.
- Placeholder READMEs for blueprints 01–04, 06 and 07, plus the Part IV pillars; Part II catalogue
  with every repo link checked.
- Docs: overview, requirements mapping, architecture, setup, troubleshooting, glossary, version
  notes, progress. AGENTS.md, CONTRIBUTING.md, verify scripts.
