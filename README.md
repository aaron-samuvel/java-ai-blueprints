# java-ai-blueprints

Enterprise Java & Spring Boot AI systems, built as working code you can run offline.

This repository turns the *Enterprise Java & Spring Boot AI Systems* handbook into a Maven
multi-module project. It has a folder per topic, with theory, runnable examples, exercises,
a project and tests. Every module builds and tests **without Docker, cloud accounts or a live LLM**:
tests use deterministic stub models from [`shared/`](shared). Live Ollama tests are opt-in (`-Pit`).

> **Status:** foundation milestone. Part I, Blueprint 03 (modular RAG) and Blueprint 05 (PII advisor) are done; the rest are planned.
> See [docs/project-progress.md](docs/project-progress.md).

## Stack

| Item | Version |
|---|---|
| Java | 21 (LTS) |
| Spring Boot | 4.0.8 |
| Spring AI | 2.0.1 |
| Maven | 3.9.x |
| Local models (optional) | Ollama `llama3.2:3b`, `nomic-embed-text` |

The reasons for these versions are in [docs/version-notes.md](docs/version-notes.md).

## Module map

| Folder | Handbook section | Status |
|---|---|---|
| [`shared/`](shared) | (support code) stub `ChatModel`/`EmbeddingModel`, common records | done |
| [`part-1-lifecycle/`](part-1-lifecycle) | Part I: J-AIDLC, five pillars, framework matrix | done |
| [`part-2-github-gems/`](part-2-github-gems) | Part II: 10 open-source repos | catalogue |
| [`blueprint-01-goap-finops-incident-agent/`](blueprint-01-goap-finops-incident-agent) | Blueprint 1 | planned |
| [`blueprint-02-langgraph4j-hitl-evaluation/`](blueprint-02-langgraph4j-hitl-evaluation) | Blueprint 2 | planned |
| [`blueprint-03-modular-rag/`](blueprint-03-modular-rag) | Blueprint 3 | **done** |
| [`blueprint-04-transactional-swarm/`](blueprint-04-transactional-swarm) | Blueprint 4 | planned |
| [`blueprint-05-pii-tokenization-advisor/`](blueprint-05-pii-tokenization-advisor) | Blueprint 5 | **done** |
| [`blueprint-06-mcp-mesh-model-switchboard/`](blueprint-06-mcp-mesh-model-switchboard) | Blueprint 6 | planned |
| [`blueprint-07-a2a-nl2sql-agent/`](blueprint-07-a2a-nl2sql-agent) | Blueprint 7 | planned |
| [`pillar-finops-budget/`](pillar-finops-budget) | Part IV: FinOps budget advisor | planned |
| [`pillar-eval-gates/`](pillar-eval-gates) | Part IV: JUnit evaluation gates | planned |
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
```

Live tests against local Ollama: `mvn -B -Pit verify` (see [docs/setup.md](docs/setup.md)).

## Docs

[Overview](docs/overview.md) · [Requirements mapping](docs/pdf-requirements-mapping.md) ·
[Architecture](docs/architecture.md) · [Setup](docs/setup.md) ·
[Troubleshooting](docs/troubleshooting.md) · [Glossary](docs/glossary.md) ·
[Version notes](docs/version-notes.md) · [Progress](docs/project-progress.md)

## Licence

[MIT](LICENSE) © 2026 Aaron Samuvel Paul
