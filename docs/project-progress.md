# Project progress

Last updated: 17 Oct 2026 (IST)

## Milestones
| # | Milestone | Status |
|---|---|---|
| M0 | Discovery & plan | ✅ done (from reading notes; PDF unavailable) |
| M1 | Foundation: root build, shared, docs, Part I, Blueprint 05, placeholders | ✅ done, pushed |
| M2 | Blueprint 03 modular RAG (+ TOON measurement) | ✅ done, pushed |
| M3 | Blueprint 07 NL2SQL guard; FinOps budget pillar | ✅ done, pushed (`b58bce7`) |
| M4 | Eval gates pillar; Blueprints 02, 04 | ✅ pushed (`6ff8f10`) |
| M5 | Blueprints 01, 06; handover docs | ✅ done, pushed (`65e9350`) |
| M6 | GitHub delivery (public repo `aaron-samuvel/java-ai-blueprints`) | ✅ public, all milestones pushed |
| M7 | Live `-Pit` run on the Windows PC | ✅ BUILD SUCCESS, 3 live tests pass (fixes `4cc075d` … `e53f8b3`) |
| M8 | Run each app on Windows | ✅ all start and answer; fixes `f0a9ebe`, `ee06b5e`, `ee419d4` |
| M9 | GitHub Pages showcase site | ✅ live (`24ec17a`, `61cfcf1`) |
| M10 | Audit: CI, Part II module, run-command fixes, docs | ✅ done (`d32b529` onward, see below) |

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

## M5 verification
`mvn -B clean verify` on the build box (2026-10-17): **BUILD SUCCESS, 122 tests, 0 failures, 0 errors, 0 skipped**
(M4's 103, plus blueprint-01 10 and blueprint-06 9). `mvn -Pit test-compile` succeeds. The live ITs were not run.

## M7–M9 verification (17 Oct 2026, IST)
- `mvn -B -Pit clean verify` on the owner's Windows PC: BUILD SUCCESS, 123 unit tests + 3 live tests
  (`PgVectorRagIT`, `OllamaPiiIT`, `OllamaJudgeIT`: golden relevancy 80%, facts 100%, all 3 regressions caught). Details in `handover.md`.
- Every app was packaged, started and called over HTTP on Windows. Fixed: Blueprint 02 review history lost repeated
  entries (`f0a9ebe`); Blueprint 03 jar start-up failed on directory entries (`ee06b5e`); Blueprint 06 ignored
  `BLUEPRINTS_DEV_JWT_SECRET` (`ee419d4`).
- Showcase site published with GitHub Pages (`24ec17a`, `61cfcf1`).

## M10 audit verification (17 Oct 2026, IST)
- Found and fixed: `mvn -pl <app> -am spring-boot:run` failed on the parent POM (`d32b529`); no way to mint a
  Blueprint 06 dev token locally (`5b95081`); Part II had only a README (`ac6966e`); no CI (`a3b1c0f`); stale docs.
- `mvn -B clean verify` on the build box: **BUILD SUCCESS, 134 tests, 0 failures** (13 reactor projects).
- Every app started with `-am spring-boot:run` on the build box and answered its README request (stub models).
- `GitHubCatalogueIT` (`-Pit`, live GitHub API) passed on the build box.

## Open items
- Re-check the mapping against the original PDF when it's available.
- Re-run `mvn -B -Pit verify` on Windows to cover the tests added in M10.
