# Supporting environment (companion guide)

This page follows the companion runbook, *Pre-Installation & Supporting Environment Flow Guide*
(10 pages), layer by layer and step by step. Each step says what this repository provides and whether it was run.
The status words match the [traceability matrix](pdf-requirements-mapping.md): **Done**, **Partial**,
**Substituted** (with the reason) and **Not done**.

**The short version:** nothing in the supporting environment is needed for `mvn -B verify`. Every module
runs on deterministic stub models and in-memory stores. The environment is only for live runs (`-Pit`)
and for running the apps against real services.

## Files

| File | Guide section | Purpose |
|---|---|---|
| `docker-compose.ai-support.yml` | Part II 2.1, Part IV 2 | postgres-pgvector (pg17), presidio-analyzer, nacos-standalone v2.4.2, redis-stack, otel-lgtm and ollama. Healthchecks on postgres, presidio and redis; named volumes for every stateful service |
| `infra/postgres/init.sql` | Part II, "Database initialization note" | `CREATE EXTENSION IF NOT EXISTS vector / hstore / "uuid-ossp"` on the first start of the volume |
| `.env.template` | Part II 2.2 | Every variable from the guide's matrix, empty. Copy it to `.env`, which git ignores |
| `scripts/preflight-check.sh`, `scripts/preflight-check.ps1` | Part IV 1 | Layer 1 to 5 checks; `--pull` pulls missing Ollama models; `--tier1` uses the small-laptop model set |
| `shared/.../env/SupportingEnvironmentHealthIndicator` + `AiEnvironmentConfiguration` | Part IV 3 | `/actuator/health/aiEnvironment`: chat model, embedding model, vector store, Presidio and MCP probes. The console table is printed at start-up |

## Layer by layer

| Guide step | Status | Notes |
|---|---|---|
| **Layer 1**: SDKMAN!, JDK 21/25 (GraalVM), Maven 3.9+, Gradle 9+, jbang | Partial | This repository needs JDK 21 and Maven 3.9+. The build box has Temurin 21.0.12 and Maven 3.9.16 in `~/tools`, installed without sudo (see [setup.md](setup.md)). It has no SDKMAN!, GraalVM, Gradle or jbang: none of the modules uses them, and GraalVM native images are Not done |
| Layer 1: uv/uvx, Node 22 and npx | Done (checked) | The preflight script reports uvx 0.13.1 and npx 11.21.0 on the build box. Both are only needed for the Embabel project creator and the MCP Inspector |
| **Layer 2**: Docker Desktop > 4.43.2 with Model Runner and the MCP Catalog (Brave Search, Fetch, Puppeteer, Wikipedia) | Not done | There is no Docker on the build box, and the Docker Desktop extensions need a GUI install on the user's PC, which was out of scope. The preflight script checks the Desktop version when Docker is present |
| Layer 2: Podman with `DOCKER_HOST` and `TESTCONTAINERS_RYUK_DISABLED=true` | Documented only | See "Podman" below. Not run |
| **Layer 3**: Ollama on :11434, pre-pull llama3.2, qwen2.5-coder:7b, nomic-embed-text and llava | Partial | The `ollama` service is in the compose file. The preflight script checks `/api/tags` and pulls missing models only with `--pull`, because they are multi-GB downloads. The live tests use `llama3.2:3b` and `nomic-embed-text`. llava is not used: no blueprint here is multimodal |
| Layer 3, 1.4 hardware tiers | Done (documented) | `--tier1` selects `llama3.2:3b` and `nomic-embed-text` for 8-16 GB laptops. CI runs Tier 3 with stub models instead of Testcontainers Ollama |
| **Layer 4**: PostgreSQL 17 + pgvector | Done (compose, init SQL, preflight `CREATE EXTENSION`) | Tests use H2 and `SimpleVectorStore`. `PgVectorRagIT` (Blueprint 03) runs against a real pgvector under `-Pit` |
| Layer 4: Presidio Analyzer on :5002 | Partial | A compose service and `PresidioPiiAnalyzer` (Blueprint 05) are provided. The client is tested only against a stub HTTP server and fails closed |
| Layer 4: OpenNLP | Partial | Blueprint 05 uses OpenNLP 2.5.6 dictionary NER, and loads `en-ner-*.bin` models from `OPENNLP_PERSON_MODEL` / `OPENNLP_LOCATION_MODEL` if they are set. The binaries are not bundled |
| Layer 4: Nacos 2.4+ | Substituted | A compose service is provided, but Blueprint 07 uses an in-process `LocalAgentRegistry` behind the `AgentRegistry` interface, so the build needs no registry |
| Layer 4: Azure CLI / azd | Substituted | Free local deployment instead: `Dockerfile` + `docker-compose.apps.yml` runs all seven apps ([deployment.md](deployment.md)). azd stays optional: it needs an Azure subscription (even the free tier asks for a credit card) |
| **Layer 5**: Grafana otel-lgtm (:3000, 4317, 4318) | Partial | A compose service and the `OTEL_EXPORTER_OTLP_ENDPOINT` variable are provided. `part-1-lifecycle` tests the Micrometer observations and the OpenInference span kinds. Export to LGTM was not run |
| Layer 5: Arize Phoenix | Not done | There is no Phoenix service. The OpenInference attributes are what Phoenix would read |
| Layer 5: master pre-flight provisioning prompt | Done | See the Part IV rows below |

## Part II: compose file and variables

| Guide item | Status | Notes |
|---|---|---|
| postgres-pgvector `pgvector/pgvector:pg17`, user `springai`, db `ai_enterprise_db` | Done | The password comes from `.env` (`POSTGRES_PASSWORD`) instead of being hardcoded as `springaipassword` in the file |
| presidio-analyzer `5002:3000` | Done | |
| nacos-standalone v2.4.2, `8848`, `9848`, `MODE=standalone` | Done | |
| redis-stack | Done | No module uses Redis. The FinOps ledger is in memory or JDBC (`JdbcTokenBudgetLedger`) |
| otel-lgtm `3000`, `4317`, `4318` | Done | |
| `CREATE EXTENSION vector, hstore, "uuid-ossp"` | Done | Done in `infra/postgres/init.sql`, not with an interactive `docker exec` |
| Variables: OPENAI_API_KEY / OPEN_AI_TOKEN, ANTHROPIC_API_KEY / ANTHROPIC_AI_TOKEN, MISTRAL_AI_TOKEN, AI_DASHSCOPE_API_KEY, PINECONE_TOKEN / STOCK_API_KEY, AZURE_OPENAI_API_KEY | Done (placeholders) | All of them are optional and empty. No module needs a cloud key |

## Part III: per-blueprint pre-installation

The guide's per-blueprint flows clone the **reference repositories** (java-agent-template,
langchain4j-sample and so on) and run them. This repository implements its own version of each blueprint
instead. The table maps each step to the matching command here.

| Blueprint | Guide step | Here | Status |
|---|---|---|---|
| 1 | JDK/Maven check; Docker MCP Catalog; Ollama llama3.2 + qwen2.5-coder; Embabel starters; `uvx` project creator; Spring Shell `execute "..." -p -r` | JDK/Maven check: preflight script. Embabel: Substituted by this repository's own `platform/` (Embabel 1.5.3 targets Spring Boot 4.1.1, and this repository is pinned to 4.0.8). `LlmInvocation` records play the role of `FakeOperationContext`. Shell: `execute "..." -p -r` in the `shell` profile (`IncidentShellCommands`) | Substituted (Embabel); Docker MCP Catalog Not done |
| 2 | postgres-pgvector for the checkpoint saver; Ollama or `llm.provider=mock`; langgraph4j-core; `scripts/smoke-test-workflows.sh` | LangGraph4j with `MemorySaver`, or `JdbcCheckpointSaver` with `blueprints.hitl.checkpoint=jdbc` (tested on H2 only; PostgreSQL not run). Stub model by default. `scripts/smoke-test-workflows.sh` runs strong match, mismatch and HITL pause/resume against the running app | Partial (PostgreSQL not run) |
| 3 | Java 25, Arconia Dev Services (ollama, postgresql, docling, lgtm), pgvector starter, `rag-branching` | Java 21. `PgVectorRagIT` uses env-configured Postgres. Docling is Substituted by Tika/PDFBox | Substituted (Arconia, Docling, Java 25) |
| 4 | Route A `azd up` (Cosmos DB DiskANN, Azure OpenAI); Route B local pgvector + Ollama | Route B only: Spring Data JDBC on H2/Postgres, `SimpleVectorStore` instead of DiskANN | Route A Not done; Route B Substituted (in-memory vectors) |
| 5 | JDK 21; deterministic local inspector; optional Presidio and OpenNLP models; `mvn test` round trips | `PrivacyInspectorService` with `InspectorChatModel` (no keys); Presidio client; OpenNLP dictionary NER. 32 module tests | Done, apart from the OpenNLP binaries and live Presidio (Partial) |
| 6 | JDK, Maven, npx MCP Inspector; llava; Pinecone keys; `-Pollama-ai` | MCP server over Streamable HTTP. The MCP Inspector can connect to `/mcp`, but that was not run here. llava and Pinecone are not used | Partial |
| 7 | JDK 17/21; Nacos + Postgres; DashScope key; spring-ai-alibaba agent framework 1.1.2.0, graph-core, JSqlParser | JSqlParser 5.4 AST guardrail; H2 tenant tables; local registry instead of Nacos; no Spring AI Alibaba (the patterns are built on plain Spring AI 2.0.1) | Substituted (Nacos, Spring AI Alibaba) |

## Part IV: automated provisioning prompt

| Item | Status | Where |
|---|---|---|
| `preflight-check.sh`: java >= 21, mvn >= 3.8.1, Docker daemon and Desktop >= 4.43.2, uvx, npx | Done | `scripts/preflight-check.sh` (and `.ps1` for Windows) |
| Ollama `/api/tags`; automatic `ollama pull` of missing models | Done, opt-in | Pulls only with `--pull`, because an unasked-for multi-GB download is a consequential side effect |
| Readiness of ports 5432, 5002, 8848, 6379, 3000 and 4317; `CREATE EXTENSION vector` | Done | |
| `docker-compose.ai-support.yml` + `.env.template` with healthchecks and named volumes | Done | Nacos and LGTM have no healthcheck in the file. Their readiness is checked by the preflight script instead |
| `SupportingEnvironmentHealthIndicator` at `/actuator/health/aiEnvironment` (ChatModel, EmbeddingModel, VectorStore, Presidio, MCP) + console table | Done | `shared/.../env/`, wired into Blueprints 03 and 05. Unit-tested; the Blueprint 05 README has a captured jar run |
| 4.1 port and service matrix | Done | Same ports as the guide, overridable through `*_HOST_PORT` |

Preflight output on the build box (17 Oct 2026, IST), no services running:
```
Layer 1: JVM and CLI tools
  [ OK ] java                         major 21
  [ OK ] mvn                          3.9.16
  [ OK ] uvx                          uvx 0.13.1 (x86_64-unknown-linux-gnu)
  [ OK ] npx                          11.21.0
Layer 2: container runtime
  [WARN] docker                       daemon not reachable (only needed for backing services and the live tests)
Layer 3: local models (Ollama at http://localhost:11434)
  [WARN] ollama                       not reachable (offline stub models still work; live tests need it)
Layer 4/5: backing services (docker-compose.ai-support.yml)
  [WARN] postgres-pgvector            localhost:5432 closed
  ...
Summary: 0 failed, 8 warnings
```

## Podman (Layer 2 alternative, not run)
```bash
export DOCKER_HOST=unix:///run/user/$(id -u)/podman/podman.sock
export TESTCONTAINERS_RYUK_DISABLED=true
podman compose -f docker-compose.ai-support.yml --env-file .env up -d
```
