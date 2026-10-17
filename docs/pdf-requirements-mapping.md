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
| Stage 4: dual-layer verification | stub-model tests; `pillar-eval-gates` | layer 1 done; layer 2 planned |
| Stage 5: zero-trust guardrails with FinOps | `blueprint-05-…`; `pillar-finops-budget`; `SqlGuard` (B07) | done |
| Stage 6: semantic observability with OpenInference | `part-1-lifecycle/theory.md` | theory only; planned |
| Stage 7: virtual threads & GraalVM native | `VirtualThreadFanOut`, `spring.threads.virtual.enabled` | virtual threads done; native planned |
| Pillar: virtual threads | `part-1-lifecycle` `VirtualThreadFanOut` | done |
| Pillar: records as LLM contracts | `part-1-lifecycle` `IncidentTriage` | done |
| Pillar: Advisor API | `AuditTrailAdvisor`, `PiiTokenizationAdvisor` | done |
| Pillar: Testcontainers / Arconia | `-Pit` profile, `OllamaPiiIT` | partial (Ollama IT; Testcontainers planned) |
| Pillar: unified data & transactions | `blueprint-04-transactional-swarm` | planned |
| Framework matrix: Spring AI 2.0, LangChain4j, Embabel, LangGraph4j, Spring AI Alibaba | `part-1-lifecycle/theory.md` | done |

## Part II: GitHub repositories (10)

| Handbook repo | Folder | Status |
|---|---|---|
| embabel-agent | `part-2-github-gems/README.md` #1 | catalogue ✅ |
| langgraph4j | #2 | catalogue ✅ |
| JavaAIDev/agentic-patterns | #3 | catalogue ✅ |
| thomasvitale/llm-apps-java-spring-ai + arconia | #4a, #4b (arconia is at `arconia-io/arconia`) | catalogue ✅ |
| ultramancode/spring-ai-privacy-guardrails | #5 | catalogue ✅ |
| AzureCosmosDB/multi-agent-spring-ai | #6 | catalogue ✅ |
| piomin/spring-ai-showcase | #7 | catalogue ✅ |
| iunera/ypipe | #8 | catalogue ✅ |
| alibaba/spring-ai-alibaba (JManus) | #9 | catalogue ✅ |
| rajeshkawali/Springboot-ai-playground | #10 | catalogue ✅ |

## Part III: Blueprints

| # | Handbook blueprint | Folder | Status |
|---|---|---|---|
| 1 | GOAP/OODA FinOps incident agent (Embabel) | `blueprint-01-goap-finops-incident-agent` | planned |
| 2 | LangGraph4j HITL multi-agent evaluation | `blueprint-02-langgraph4j-hitl-evaluation` | planned |
| 3 | Modular, branching, conditional RAG (Docling, PGVector) | `blueprint-03-modular-rag` | **done** (20 tests + 1 pgvector IT; Docling stubbed) |
| 4 | Multi-agent transactional swarm, tenant-partitioned ChatMemory | `blueprint-04-transactional-swarm` | planned |
| 5 | Zero-trust PII tokenisation advisor with @ToolPrivacyPolicy | `blueprint-05-pii-tokenization-advisor` | **done** (18 tests + 1 IT) |
| 6 | MCP server mesh + model switchboard (OAuth2) | `blueprint-06-mcp-mesh-model-switchboard` | planned |
| 7 | A2A mesh + self-healing NL2SQL (JSqlParser guard, LIMIT 500, 3 retries) | `blueprint-07-a2a-nl2sql-agent` | **done** (22 tests): guard, LIMIT 500, 1+3 attempts on H2; A2A-style single agent (multi-agent mesh planned) |

## Part IV

| Handbook item | Folder | Status |
|---|---|---|
| FinOps reserve-call-commit budget advisor | `pillar-finops-budget` | **done** (10 tests) |
| JUnit evaluation gates (RelevancyEvaluator, FactCheckingEvaluator) | `pillar-eval-gates` | planned |
| TOON | `blueprint-03-modular-rag` `toon/`, `ToonVsJsonTokenMeasurementTest` | done: measured 38–39% vs compact JSON on uniform tables, 16% on text chunks, −26 to −31% on nested data |
| AGENTS.md constitution | `/AGENTS.md` | done (wording is author's design) |
