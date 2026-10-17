# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/).

## [Unreleased]

### Added: free-alternative quick wins (17 Oct 2026)
Matrix now: 119 requirements, 75 Done, 23 Partial, 16 Substituted, 5 Not done. `mvn -B -o clean verify`: 244 tests.
- Blueprint 06: MCP resources (`ops://batch/jobs`, `ops://batch/executions/{executionId}`, `ops://jvm/heap`) and prompts
  (`diagnose-failed-batch-job`, `jvm-health-review`) via `@McpResource` / `@McpPrompt` (`424f6de`).
- FinOps: `RedisTokenBudgetLedger`, with Lua-atomic reserve/settle behind `TokenBudgetAuthority`; an offline fake-gateway test and a live
  `RedisTokenBudgetLedgerIT` under `-Pit` against `localhost:6379` (`9053843`).
- Deployment: one multi-stage `Dockerfile` and `docker-compose.apps.yml` for all seven apps (ports 8081-8087), with `docs/deployment.md`.
  azd is documented as optional (`0cfa173`).
- Blueprint 05: `PrivacyChatService` chains the FinOps budget advisor behind the PII advisor (`b36b197`).
- Skipped to keep scope to quick wins, and left Not done with reasons: the GraalVM native profile, the LangChain4j `@AiService` example,
  and the Docker MCP Catalog. Testcontainers ITs were also skipped; those rows keep their existing status.
- Docs: matrix, README, site, handover, progress and environment updated (`c870219`).

### Added: handbook PDF conformance pass (17 Oct 2026)
The repository was re-checked against both handbook PDFs. The full result is in `docs/pdf-requirements-mapping.md`:
119 requirements, 73 Done, 23 Partial, 15 Substituted, 8 Not done.
- Environment (companion guide): `docker-compose.ai-support.yml`, `.env.template`, `infra/postgres/init.sql`,
  `scripts/preflight-check.sh` / `.ps1`, and the `aiEnvironment` health indicator in `shared/` (`82814e7`).
  `docs/environment.md` covers the guide step by step, and `.env.template` gained the guide's alias variables (`6266f8e`).
- Blueprint 01: `CloudRemediationAgent` on a type-driven GOAP platform, `/api/v1/incidents/investigate`, Spring Shell `execute` (`ceb4eda`).
- Blueprint 02: submission review graph with 4 parallel evaluators, consensus exits, `JdbcCheckpointSaver`, node hooks and a smoke script (`db6312c`).
- Blueprint 03: multi-format idempotent ingestion, pre-retrieval transforms, branching retriever, `GroundedAnswer` citations (`113bb80`).
- Blueprint 04: commerce swarm with handoffs, transactional order and refund tools, tenant memory, SSE and `swarm.html` (`76f4fa5`).
- Blueprint 05: `pii/guardrails`, with an analyzer SPI (regex, OpenNLP, Presidio), the vault, response policies, a tool guard and the privacy inspector (`b8a20e7`).
- Blueprint 06: ops-server role (Batch introspection, JFR diagnostics, OpenAPI-to-MCP bridge) and the v2 switchboard router (`6569bfa`).
- Blueprint 07: Sequential/Parallel/Loop agents, schema index, tenant-injecting SQL guard, EXPLAIN-checked self-healing, A2A mesh (`7daa75c`).
- FinOps: `TokenBudgetAuthority` and `JdbcTokenBudgetLedger` (daily budgets, atomic conditional `UPDATE`) (`4c302df`).
- Eval gates: `GateSuite` with five gates (2 from the handbook, 3 of the author's design), and json-io TOON with a measured token comparison (`af591ba`).
- Part I: `observability/` (OpenInference span kinds on Spring AI observations) and Jackson descriptions on `IncidentTriage`.
  `AGENTS.md` was rewritten to the handbook's four-section constitution, and `CLAUDE.md` added (`5b74092`).
- Part II: the catalogue grew from 11 to 15 entries, so every repository named in the 10 rows is listed. The live check was re-run (`ae4c2d6`, `0404eaf`).

### Changed
- `docs/pdf-requirements-mapping.md` is now a page-referenced traceability matrix. The old "written from reading notes"
  caveat is removed everywhere, because the PDFs were checked directly.
- README, site, handover, progress, overview, architecture and version notes: updated test counts (240 offline) and coverage figures.

### Added
- `.github/workflows/ci.yml`: offline `mvn -B verify` on Temurin 21 for every push to `main` and every pull request,
  plus `scripts/check-links.py`, which fails on broken relative links in Markdown and the site (`a3b1c0f`).
- `part-2-github-gems/` is now a full topic module: theory, examples, exercises, `gems.csv` (licence and language from
  the GitHub API on 2026-10-17), `GemsCatalogue`, `ReuseAdvice`, the `GemsReport` CLI, 6 offline tests and a live
  `GitHubCatalogueIT` under `-Pit` (`ac6966e`).
- `blueprint-06`: `DevJwtMinter` mints local HS256 dev tokens from `BLUEPRINTS_DEV_JWT_SECRET`, with 2 tests (`5b95081`).
- GitHub Pages showcase site in `site/` with the recorded run results (`24ec17a`), deployed by
  `.github/workflows/pages.yml` (`61cfcf1`).
- `shared/README.md`; CI, Pages and licence badges in the README.

### Fixed
- Build: `mvn -pl <app> -am spring-boot:run`, the command every README documents, failed on the parent POM with
  `Unable to find a suitable main class`. The parent and libraries now skip the run goal; Part I has a runnable jar (`d32b529`).
- Docs: setup, troubleshooting, handover, progress, requirements mapping and module READMEs updated for the live run,
  the run-each-app pass and the audit (test counts, ports, `PORT` override, Blueprint 06 token steps). `setup.md`
  no longer claims the apps read `PGPASSWORD` (only `PgVectorRagIT` does).

Found by the live `-Pit` run on Windows (2026-10-17): Blueprint 03 `it` profile DataSource (`4cc075d`), failsafe and
repackaged jars (`df077dc`), Blueprint 05 duplicate `ChatModel` (`89e443c`), small-judge verdict parsing and model
name (`2433c55`, `265813e`, `f647cd7`), and a shorter relevancy prompt for small judges (`e53f8b3`).

### Fixed (run-each-app pass)
Found by starting each packaged app on Windows and calling it over HTTP (2026-10-17):
- `blueprint-03`: startup failed from the packaged jar with `no parser for ` because the corpus pattern also
  matched jar directory entries; they are now skipped (regression test ingests from a real jar) (`ee06b5e`).
- `blueprint-06`: the documented `BLUEPRINTS_DEV_JWT_SECRET` env var was never read (no `JwtDecoder`, app did not
  start); it is now mapped in `application.properties` and the dev decoder turns on only for a non-blank secret (`ee419d4`).
- `blueprint-02`: the review `history` lost repeated entries (`Channels.appender` drops duplicates); it now uses
  `appenderWithDuplicate` (`f0a9ebe`).

### Added (milestones M2–M5)
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

- `blueprint-01-goap-finops-incident-agent/` (M5): A* GOAP planner with negative preconditions; OODA FinOps
  incident agent (re-plan every cycle, LLM only for diagnosis, escalation fallback); 10 tests.
- `blueprint-06-mcp-mesh-model-switchboard/` (M5): Spring AI MCP server nodes (Streamable HTTP) behind JWT scopes,
  `McpMesh` client merging tools across nodes, scope-aware model switchboard with downgrade; 9 tests.
- `docs/handover.md` (M5).
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
