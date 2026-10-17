# Handover

This is the state at the end of M5 (2026-10-17, IST): every module is implemented. Anything not covered by the handbook notes is labelled *author's design (not from handbook)* in the module docs.

## 1. Prerequisites on the Windows PC
* JDK 21 (Temurin) and Maven 3.9+ on `PATH`. See [`setup.md`](setup.md).
* Optional, for the live tests only:
  * Ollama, with the models listed in `setup.md`.
  * PostgreSQL with pgvector on port 15432, user `dev`. Set the password in your shell as `$env:PGPASSWORD`; it is never written in the repo.

## 2. Build and test everything (offline)
```powershell
.\scripts\verify.ps1        # or: mvn -B clean verify
```
No network model, database or secret is needed. Every test uses the stub models from `shared/`.

## 3. Run each module (PowerShell, from the repo root)
| Module | Command / entry point | Notes |
|---|---|---|
| part-1-lifecycle | `mvn -B -pl part-1-lifecycle -am test` | Library + tests (lifecycle, virtual threads) |
| blueprint-01 | `mvn -B -pl blueprint-01-goap-finops-incident-agent -am spring-boot:run` | Port 8081; `POST /api/incidents/run` with a CSV (see module README) |
| blueprint-02 | `mvn -B -pl blueprint-02-langgraph4j-hitl-evaluation -am test` | Library + tests (LangGraph4j HITL) |
| blueprint-03 | `mvn -B -pl blueprint-03-modular-rag -am test` | Live pgvector path: `-Pit verify` (needs Postgres + Ollama) |
| blueprint-04 | `mvn -B -pl blueprint-04-transactional-swarm -am test` | Uses H2 in memory |
| blueprint-05 | `mvn -B -pl blueprint-05-pii-tokenization-advisor -am test` | Live: `-Pit verify` (needs Ollama) |
| blueprint-06 | set `$env:BLUEPRINTS_DEV_JWT_SECRET` (≥32 chars, random), then `mvn -B -pl blueprint-06-mcp-mesh-model-switchboard -am spring-boot:run` | Port 8086; MCP at `/mcp`; needs a JWT signed with that secret |
| blueprint-07 | `mvn -B -pl blueprint-07-a2a-nl2sql-agent -am test` | Uses H2 in memory |
| pillar-finops-budget | `mvn -B -pl pillar-finops-budget -am test` | |
| pillar-eval-gates | `mvn -B -pl pillar-eval-gates -am test` | Live judge: `-Pit verify` (needs Ollama) |

The exact ports and run commands for each module are in its README.

## 4. What has been verified, and what hasn't
* ✅ `mvn -B clean verify` on the Linux build box: **BUILD SUCCESS, 122 tests, 0 failures**.
* ✅ `mvn -B -Pit test-compile`: the live integration tests compile.
* ❌ **The live `-Pit` tests have never been run** (`OllamaPiiIT`, `PgVectorRagIT`, `OllamaJudgeIT`). No Ollama or Postgres was available on the build box.
* ❌ Nothing has been run on Windows yet. `verify.ps1` has not been executed.
* ❌ The mapping was written from notes, not from the original handbook PDF, which was unavailable.
* ❌ Embabel's compatibility was checked only from its POM (1.5.3 → Boot 4.1.1). It was not run.

## 5. Known limitations
* Blueprint 01 uses its own GOAP planner instead of Embabel (see its README).
* Blueprint 06 validates HS256 dev JWTs only. A real IdP (`issuer-uri`) is left as an exercise. There's no token-minting CLI.
* Blueprint 07 is an A2A-style single agent. A multi-agent A2A mesh is not implemented.
* Stage 6 (OpenInference semantic observability) is theory only. GraalVM native (Stage 7) is not built.
* The Testcontainers/Arconia pillar is partial: the live ITs expect services you start yourself.
* Docling is stubbed (pre-converted Markdown).
* Spring AI 2.0.1 was built against Boot 4.1.1. It works on 4.0.8 in these tests, but that combination is not an official pairing.

## 6. Suggested next steps
1. On Windows, run `.\scripts\verify.ps1`, then start Ollama and Postgres, set `PGPASSWORD`, and run `mvn -B -Pit verify`. Record the real results in `project-progress.md`.
2. Re-check `pdf-requirements-mapping.md` against the handbook PDF.
3. Decide whether to move the repo to Boot 4.1.x. That would allow trying Embabel for Blueprint 01.
4. Add Testcontainers for Postgres/pgvector and Ollama, so the ITs can run in CI.
5. Add a GitHub Actions workflow that runs `mvn -B verify` (offline tests only).

## Live verification on Windows (17 Oct 2026, IST)

`mvn -B -Pit clean verify` was run on the owner's Windows 11 PC (Temurin 21, Maven 3.10, Docker Postgres 16 + pgvector on port 15432, Ollama with llama3.2:3b and nomic-embed-text). Result: **BUILD SUCCESS, all 12 modules, 123 offline tests + 3 live integration tests**.

| Live test | Result |
|---|---|
| `PgVectorRagIT` (pgvector + nomic-embed-text) | pass |
| `OllamaPiiIT` (llama3.2:3b through the PII advisor) | pass (~20–48 s on CPU) |
| `OllamaJudgeIT` (llama3.2:3b as judge) | pass: golden set relevancy 80%, facts 100%; all 3 regressions caught |

Fixes found by the live run (all pushed):
- Blueprint 03: the `it` profile pulled in `spring-boot-starter-jdbc`, which made the app test demand a DataSource; it now uses plain `spring-jdbc`.
- Failsafe now uses `target/classes`, because Spring Boot's repackaged jar hides classes under `BOOT-INF/`.
- Blueprint 05: Ollama chat auto-config is only enabled when `blueprints.model=ollama` (it previously created a second `ChatModel`).
- Eval gates: small judges reply "Yes." rather than "yes", so `VerdictNormalizingChatModel` reduces replies to YES/NO. Spring AI's default relevancy prompt also made llama3.2:3b answer NO to correct answers, so the live gate uses `EvaluationGate.SMALL_JUDGE_RELEVANCY`.
