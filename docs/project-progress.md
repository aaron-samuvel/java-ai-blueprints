# Project progress

Last updated: 17 Oct 2026 (IST)

## Milestones
| # | Milestone | Status |
|---|---|---|
| M0 | Discovery & plan | ✅ done (from reading notes; PDF unavailable) |
| M1 | Foundation: root build, shared, docs, Part I, Blueprint 05, placeholders | ✅ done locally, **not pushed** |
| M2 | Blueprint 03 modular RAG (+ TOON measurement) | ✅ done locally, **not pushed** |
| M3 | Blueprint 07 NL2SQL guard; FinOps budget pillar | ⏳ next |
| M4 | Eval gates pillar; Blueprints 02, 04 | planned |
| M5 | Blueprints 01, 06 | planned |
| M6 | GitHub delivery (public repo `aaron-samuvel/java-ai-blueprints`) | waiting for the user to sign in to GitHub |

## M1 verification
`mvn -B verify` on the build box (Temurin 21.0.12.1, Maven 3.9.16): **BUILD SUCCESS, 24 tests,
0 failures, 0 errors, 0 skipped** (shared 2, part-1 4, blueprint-05 18).
`mvn -Pit test-compile` compiles the live IT; it wasn't run (no Ollama on the build box).

## M2 verification
`mvn -B verify` on the build box: **BUILD SUCCESS, 44 tests, 0 failures, 0 errors, 0 skipped**
(shared 2, part-1 4, blueprint-03 20, blueprint-05 18). `mvn -Pit test-compile` compiles both live ITs
(`OllamaPiiIT`, `PgVectorRagIT`); neither was run (no Ollama or Postgres on the build box).
The measured TOON numbers are in `blueprint-03-modular-rag/theory.md`.

## Open items
- Run `mvn -Pit verify` on the Windows PC with Ollama and Postgres (pgvector extension) running, with `PGPASSWORD` set in the shell.
- Re-check the mapping against the original PDF when it's available.
