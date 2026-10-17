# Project progress

Last updated: 17 Oct 2026 (IST)

## Milestones
| # | Milestone | Status |
|---|---|---|
| M0 | Discovery & plan | ✅ done (from reading notes; PDF unavailable) |
| M1 | Foundation: root build, shared, docs, Part I, Blueprint 05, placeholders | ✅ done locally, **not pushed** |
| M2 | Blueprint 03 modular RAG (+ TOON measurement) | ✅ done locally, **not pushed** |
| M3 | Blueprint 07 NL2SQL guard; FinOps budget pillar | ✅ done, pushed (`b58bce7`) |
| M4 | Eval gates pillar; Blueprints 02, 04 | ✅ done locally, **not pushed** (awaiting review) |
| M5 | Blueprints 01, 06 | ⏳ next |
| M6 | GitHub delivery (public repo `aaron-samuvel/java-ai-blueprints`) | repo public; M1–M3 pushed (up to `b58bce7`); M4 local, pending review |

## M1 verification
`mvn -B verify` on the build box (Temurin 21.0.12.1, Maven 3.9.16): **BUILD SUCCESS, 24 tests,
0 failures, 0 errors, 0 skipped** (shared 2, part-1 4, blueprint-05 18).
`mvn -Pit test-compile` compiles the live IT; it wasn't run (no Ollama on the build box).

## M2 verification
`mvn -B verify` on the build box: **BUILD SUCCESS, 44 tests, 0 failures, 0 errors, 0 skipped**
(shared 2, part-1 4, blueprint-03 20, blueprint-05 18). `mvn -Pit test-compile` compiles both live ITs
(`OllamaPiiIT`, `PgVectorRagIT`); neither was run (no Ollama or Postgres on the build box).
The measured TOON numbers are in `blueprint-03-modular-rag/theory.md`.

## M3 verification
`mvn -B clean verify` on the build box: **BUILD SUCCESS, 76 tests, 0 failures, 0 errors, 0 skipped**
(shared 2, part-1 4, blueprint-03 20, blueprint-05 18, blueprint-07 22, pillar-finops-budget 10).
`FinOpsDemo` was run; its real output is in `pillar-finops-budget/examples/README.md`.

## M4 verification
`mvn -B clean verify` on the build box: **BUILD SUCCESS, 103 tests, 0 failures, 0 errors, 0 skipped**
(shared 2, part-1 4, blueprint-03 20, blueprint-05 18, blueprint-07 22, finops 10, eval-gates 12,
blueprint-02 7, blueprint-04 8). `-Pit` test sources compile (including `OllamaJudgeIT`); not run on the box.

## Open items
- Run `mvn -Pit verify` on the Windows PC with Ollama and Postgres (pgvector extension) running, with `PGPASSWORD` set in the shell.
- Re-check the mapping against the original PDF when it's available.
