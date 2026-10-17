# Handover

This is the state on 17 Oct 2026 (IST), after the live run, the run-each-app pass, the Pages site, the audit and the conformance pass against both handbook PDFs. Every module is implemented. Each handbook requirement has a page reference and a status in [`pdf-requirements-mapping.md`](pdf-requirements-mapping.md): 119 requirements, 75 Done, 23 Partial, 16 Substituted and 5 Not done. Anything the PDFs do not specify is labelled *author's design (not from handbook)* in the module docs.

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
Every app starts with `mvn -B -pl <module> -am spring-boot:run` (the parent POM and libraries skip the run goal).
Ports default to 808N and can be changed with `$env:PORT`.

| Module | Command / entry point | Port, sample request |
|---|---|---|
| part-1-lifecycle | `mvn -B -pl part-1-lifecycle -am spring-boot:run` (or `java -jar` on the packaged jar) | CLI, prints an `IncidentTriage` record |
| part-2-github-gems | `mvn -B -pl part-2-github-gems -am spring-boot:run` | CLI, prints the catalogue with licence advice |
| blueprint-01 | `mvn -B -pl blueprint-01-goap-finops-incident-agent -am spring-boot:run` | 8081; `POST /api/incidents/run` with `billing-sample.csv` (`text/csv`) |
| blueprint-02 | `mvn -B -pl blueprint-02-langgraph4j-hitl-evaluation -am spring-boot:run` | 8082; `POST /api/reviews`, then `POST /api/reviews/{id}/decision` |
| blueprint-03 | `mvn -B -pl blueprint-03-modular-rag -am spring-boot:run` | 8083; `POST /api/ask` |
| blueprint-04 | `mvn -B -pl blueprint-04-transactional-swarm -am spring-boot:run` | 8084; `POST /api/conversations/{id}/messages` with `X-Tenant-Id` |
| blueprint-05 | `mvn -B -pl blueprint-05-pii-tokenization-advisor -am spring-boot:run` | 8085; `POST /api/chat` |
| blueprint-06 | set `$env:BLUEPRINTS_DEV_JWT_SECRET` (≥32 chars, random), then `mvn -B -pl blueprint-06-mcp-mesh-model-switchboard -am spring-boot:run` | 8086; `POST /api/switchboard/ask` with a token from `DevJwtMinter`; MCP at `/mcp` |
| blueprint-07 | `mvn -B -pl blueprint-07-a2a-nl2sql-agent -am spring-boot:run` | 8087; `GET /.well-known/agent.json`, `POST /a2a/tasks` |
| pillar-finops-budget | `mvn -q -pl pillar-finops-budget -am install -DskipTests`, then `mvn -q -pl pillar-finops-budget exec:java -Dexec.mainClass=io.github.aaronsamuvel.blueprints.finops.FinOpsDemo` | CLI demo |
| pillar-eval-gates | `mvn -B -pl pillar-eval-gates -am verify` | Library; live judge with `-Pit` |

The exact requests and expected output for each module are in its README.

## 4. What has been verified, and what hasn't
* ✅ `mvn -B -o clean verify` on the Linux build box (17 Oct 2026, after the PDF conformance pass): **BUILD SUCCESS, 13 reactor projects, 244 tests, 0 failures, 0 errors, 0 skipped**.
* ✅ The same offline build runs in GitHub Actions (`.github/workflows/ci.yml`) on every push and pull request, together with a relative-link check (`scripts/check-links.py`).
* ✅ `mvn -B -Pit clean verify` on the owner's Windows PC: BUILD SUCCESS with 123 unit tests and the 3 live tests (details below). That run predates the audit's new tests.
* ✅ Each app was packaged, started on Windows and called over HTTP (results on the [showcase site](https://aaron-samuvel.github.io/java-ai-blueprints/)). That pass found 3 bugs, fixed in `f0a9ebe`, `ee06b5e` and `ee419d4`.
* ✅ The audit re-ran every app with `mvn -pl <app> -am spring-boot:run` on the build box (stub models) and found the `-am` run bug, fixed in `d32b529`.
* ✅ `GitHubCatalogueIT` (Part II, live GitHub API) passed on the build box: 15 repositories, HTTP 200, licences as recorded.
* ✅ The traceability matrix was built from both PDFs directly (18-page handbook, 10-page environment guide).
* ❌ The handbook packages added in the conformance pass were not run on Windows or with live models. They are covered by offline tests only.
* ❌ Embabel's compatibility was checked only from its POM (1.5.3 → Boot 4.1.1). It was not run.

## 5. Known limitations
* Blueprint 01 uses its own GOAP planner instead of Embabel (see its README).
* Blueprint 06 validates HS256 dev JWTs only (mint them locally with `DevJwtMinter`). A real IdP (`issuer-uri`) is left as an exercise.
* Blueprint 07's A2A support is a subset: JSON-RPC `message/send` and agent cards, with no streaming, `tasks/get` or push notifications. Nacos is replaced by an in-process registry.
* Stage 6 observability is tested in-process (Micrometer observations with OpenInference span kinds). OTLP export to LGTM or Phoenix was not run. GraalVM native (Stage 7) is not built.
* The Testcontainers/Arconia pillar is substituted: the live ITs expect services you start from `docker-compose.ai-support.yml`.
* Docling is substituted by Tika/PDFBox.
* Blueprint 06 exposes MCP tools only (no MCP resources or prompts) and one transport per node.
* FinOps has in-memory and JDBC ledgers. Redis and a Cycles authority service are not implemented.
* Evaluation gates 3-5 (structured output, PII leak, token budget) are author's design: the handbook's cover promises five gates but names two.
* Spring AI 2.0.1 was built against Boot 4.1.1. It works on 4.0.8 in these tests, but that combination is not an official pairing.

## 6. Suggested next steps
1. Work through the Partial and Not done rows in `pdf-requirements-mapping.md`.
2. Decide whether to move the repo to Boot 4.1.x. That would allow trying Embabel for Blueprint 01.
3. Add Testcontainers for Postgres/pgvector and Ollama, so the live ITs can also run in CI.
4. Re-run `mvn -B -Pit verify` on Windows after pulling, to cover the tests added by the audit.

## Showcase site
The static site in `site/` is deployed by `.github/workflows/pages.yml` (added in `24ec17a`, workflow in `61cfcf1`)
to https://aaron-samuvel.github.io/java-ai-blueprints/. It documents recorded results; the apps run locally.

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
