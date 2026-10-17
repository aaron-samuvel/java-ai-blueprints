# java-ai-blueprints

[![CI](https://github.com/aaron-samuvel/java-ai-blueprints/actions/workflows/ci.yml/badge.svg)](https://github.com/aaron-samuvel/java-ai-blueprints/actions/workflows/ci.yml)
[![Pages](https://github.com/aaron-samuvel/java-ai-blueprints/actions/workflows/pages.yml/badge.svg)](https://aaron-samuvel.github.io/java-ai-blueprints/)
[![Licence: MIT](https://img.shields.io/badge/licence-MIT-blue.svg)](LICENSE)

Enterprise Java & Spring Boot AI systems, built as working code you can run offline.

**Showcase site:** [aaron-samuvel.github.io/java-ai-blueprints](https://aaron-samuvel.github.io/java-ai-blueprints/) (documents each module with real recorded results; the apps themselves run locally).

This repository turns the *Enterprise Java & Spring Boot AI Systems* handbook into a Maven
multi-module project. It has a folder per topic, with theory, runnable examples, exercises,
a project and tests. Every module builds and tests **without Docker, cloud accounts or a live LLM**:
tests use deterministic stub models from [`shared/`](shared). Live Ollama tests are opt-in (`-Pit`).

> **Status:** all modules done. Part I, Part II, Blueprints 01–07 and both Part IV pillars are implemented with offline tests (134 today; CI runs them on every push and pull request). The full `mvn -B -Pit clean verify` (123 unit + 3 live tests against pgvector and Ollama, at the time) passed on Windows on 17 Oct 2026, and every app was started and called over HTTP. See [`docs/handover.md`](docs/handover.md).
> See [docs/project-progress.md](docs/project-progress.md).

## Stack

| Item | Version |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.0.8 |
| Spring AI | 2.0.1 |
| Maven | 3.9.x |
| JSqlParser | 5.4 (Blueprint 07) |
| LangGraph4j | 1.9.5 (Blueprint 02) |
| H2 | from the Boot BOM (offline DB for Blueprint 07) |
| Local models (optional) | Ollama `llama3.2:3b`, `nomic-embed-text` |

The reasons for these versions are in [docs/version-notes.md](docs/version-notes.md).

## Module map

| Folder | Handbook section | Status |
|---|---|---|
| [`shared/`](shared) | (support code) stub `ChatModel`/`EmbeddingModel`, common records | done |
| [`part-1-lifecycle/`](part-1-lifecycle) | Part I: J-AIDLC, five pillars, framework matrix | done |
| [`part-2-github-gems/`](part-2-github-gems) | Part II: 10 open-source repos (catalogue, licences, link checks) | done |
| [`blueprint-01-goap-finops-incident-agent/`](blueprint-01-goap-finops-incident-agent) | Blueprint 1 | done |
| [`blueprint-02-langgraph4j-hitl-evaluation/`](blueprint-02-langgraph4j-hitl-evaluation) | Blueprint 2 | **done** |
| [`blueprint-03-modular-rag/`](blueprint-03-modular-rag) | Blueprint 3 | **done** |
| [`blueprint-04-transactional-swarm/`](blueprint-04-transactional-swarm) | Blueprint 4 | **done** |
| [`blueprint-05-pii-tokenization-advisor/`](blueprint-05-pii-tokenization-advisor) | Blueprint 5 | **done** |
| [`blueprint-06-mcp-mesh-model-switchboard/`](blueprint-06-mcp-mesh-model-switchboard) | Blueprint 6 | done |
| [`blueprint-07-a2a-nl2sql-agent/`](blueprint-07-a2a-nl2sql-agent) | Blueprint 7 | **done** |
| [`pillar-finops-budget/`](pillar-finops-budget) | Part IV: FinOps budget advisor | **done** |
| [`pillar-eval-gates/`](pillar-eval-gates) | Part IV: JUnit evaluation gates | **done** |
| [`AGENTS.md`](AGENTS.md) | Part IV: AGENTS.md constitution | done |
| [`docs/`](docs) | Overview, mapping, architecture, setup, troubleshooting, glossary | done |

## Quickstart

```bash
# needs JDK 21 and Maven 3.9+
./scripts/verify.sh            # or: mvn -B verify
# Windows PowerShell
./scripts/verify.ps1

# run Blueprint 05 offline (stub model) on http://localhost:8085
mvn -pl blueprint-05-pii-tokenization-advisor -am spring-boot:run
curl -s -X POST localhost:8085/api/chat -H 'Content-Type: application/json' \
     -d '{"message":"Mail me at priya.sharma@example.com"}'

# run Blueprint 03 (modular RAG) offline on http://localhost:8083
mvn -pl blueprint-03-modular-rag -am spring-boot:run
curl -s -X POST localhost:8083/api/ask -H 'Content-Type: application/json' \
     -d '{"question":"How much sick leave do I get?"}'
```

Ports: Blueprint 0N listens on 808N (01 → 8081 … 07 → 8087); override with `PORT`. Blueprint 06 also needs
`BLUEPRINTS_DEV_JWT_SECRET` (see its README). Each module README has its sample requests.

Live tests against local Ollama: `mvn -B -Pit verify` (see [docs/setup.md](docs/setup.md)).

## Docs

[Overview](docs/overview.md) · [Requirements mapping](docs/pdf-requirements-mapping.md) ·
[Architecture](docs/architecture.md) · [Setup](docs/setup.md) ·
[Troubleshooting](docs/troubleshooting.md) · [Glossary](docs/glossary.md) ·
[Version notes](docs/version-notes.md) · [Progress](docs/project-progress.md)

## Licence

[MIT](LICENSE) © 2026 Aaron Samuvel Paul
