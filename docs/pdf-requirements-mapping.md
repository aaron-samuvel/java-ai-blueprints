# Handbook requirements mapping

> **Note on sources:** this mapping was built from **reading notes** on the handbook, because the
> original PDF file wasn't available when the repository was created. Section titles and lists
> follow those notes. Anything the notes don't cover is marked *author's design (not from handbook)*
> in the module docs. When the PDF is available again, re-check this table against it.

Status: **done** = implemented and tested · **catalogue** = documentation only, links verified ·
**planned** = placeholder README.

## Part I: Lifecycle, pillars, frameworks

| Handbook item | Folder / file | Status |
|---|---|---|
| J-AIDLC stage 1: domain/intent modelling with records | `part-1-lifecycle` `IncidentTriage`, `StructuredOutputExample` | done |
| Stage 2: hermetic local prototyping with Dev Services | `shared/` stubs; `-Pit` profile; `docs/setup.md` | done (stubs), ITs partial |
| Stage 3: context engineering & RAG ETL with TOON | `blueprint-03-modular-rag` (`IngestionService`, `ToonEncoder`) | done |
| Stage 4: dual-layer verification | stub-model tests; `pillar-eval-gates` | done |
| Stage 5: zero-trust guardrails with FinOps | `blueprint-05-…`; `pillar-finops-budget`; `SqlGuard` (B07) | done |
| Stage 6: semantic observability with OpenInference | `part-1-lifecycle/theory.md` | theory only; planned |
| Stage 7: virtual threads & GraalVM native | `VirtualThreadFanOut`, `spring.threads.virtual.enabled` | virtual threads done; native planned |
| Pillar: virtual threads | `part-1-lifecycle` `VirtualThreadFanOut` | done |
| Pillar: records as LLM contracts | `part-1-lifecycle` `IncidentTriage` | done |
| Pillar: Advisor API | `AuditTrailAdvisor`, `PiiTokenizationAdvisor` | done |
| Pillar: Testcontainers / Arconia | `-Pit` profile, `OllamaPiiIT` | partial (Ollama IT; Testcontainers planned) |
| Pillar: unified data & transactions | `blueprint-04-transactional-swarm` | done |
| Framework matrix: Spring AI 2.0, LangChain4j, Embabel, LangGraph4j, Spring AI Alibaba | `part-1-lifecycle/theory.md` | done |

## Part II: GitHub repositories (10)

Part II is a full topic folder: README catalogue, theory, examples, exercises, a small project (`GemsCatalogue`,
`GemsReport`), 6 offline tests and a live GitHub API check (`GitHubCatalogueIT`, `-Pit`).

| Handbook repo | Folder | Status |
|---|---|---|
| embabel-agent | `part-2-github-gems/README.md` #1 (also `gems.csv`, tested by `GemsCatalogueTest`) | done ✅ |
| langgraph4j | #2 | done ✅ |
| JavaAIDev/agentic-patterns | #3 | done ✅ |
| thomasvitale/llm-apps-java-spring-ai + arconia | #4a, #4b (arconia is at `arconia-io/arconia`) | done ✅ |
| ultramancode/spring-ai-privacy-guardrails | #5 | done ✅ |
| AzureCosmosDB/multi-agent-spring-ai | #6 | done ✅ |
| piomin/spring-ai-showcase | #7 | done ✅ |
| iunera/ypipe | #8 | done ✅ |
| alibaba/spring-ai-alibaba (JManus) | #9 | done ✅ |
| rajeshkawali/Springboot-ai-playground | #10 | done ✅ |

## Part III: Blueprints

| # | Handbook blueprint | Folder | Status |
|---|---|---|---|
| 1 | GOAP/OODA FinOps incident agent (Embabel) | `blueprint-01-goap-finops-incident-agent` | **done** (10 tests): own GOAP A* + OODA loop; Embabel not used (1.5.3 targets Boot 4.1.1, see module README) |
| 2 | LangGraph4j HITL multi-agent evaluation | `blueprint-02-langgraph4j-hitl-evaluation` | **done** (7 tests; LangGraph4j 1.9.5) |
| 3 | Modular, branching, conditional RAG (Docling, PGVector) | `blueprint-03-modular-rag` | **done** (21 tests + 1 pgvector IT; Docling stubbed) |
| 4 | Multi-agent transactional swarm, tenant-partitioned ChatMemory | `blueprint-04-transactional-swarm` | **done** (8 tests; H2, @Transactional rollback) |
| 5 | Zero-trust PII tokenisation advisor with @ToolPrivacyPolicy | `blueprint-05-pii-tokenization-advisor` | **done** (18 tests + 1 IT) |
| 6 | MCP server mesh + model switchboard (OAuth2) | `blueprint-06-mcp-mesh-model-switchboard` | **done** (13 tests): Spring AI MCP Streamable HTTP nodes, 2-node mesh, JWT scopes (HS256 dev decoder), switchboard downgrade, local `DevJwtMinter` |
| 7 | A2A mesh + self-healing NL2SQL (JSqlParser guard, LIMIT 500, 3 retries) | `blueprint-07-a2a-nl2sql-agent` | **done** (22 tests): guard, LIMIT 500, 1+3 attempts on H2; A2A-style single agent (multi-agent mesh planned) |

## Part IV

| Handbook item | Folder | Status |
|---|---|---|
| FinOps reserve-call-commit budget advisor | `pillar-finops-budget` | **done** (10 tests) |
| JUnit evaluation gates (RelevancyEvaluator, FactCheckingEvaluator) | `pillar-eval-gates` | **done** (13 tests + Ollama judge IT) |
| TOON | `blueprint-03-modular-rag` `toon/`, `ToonVsJsonTokenMeasurementTest` | done: measured 38–39% vs compact JSON on uniform tables, 16% on text chunks, −26 to −31% on nested data |
| AGENTS.md constitution | `/AGENTS.md` | done (wording is author's design) |
