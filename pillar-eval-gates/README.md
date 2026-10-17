# Part IV pillar: JUnit evaluation gates

**Status:** planned · **Handbook:** Part IV

## Planned scope (from handbook notes)
- JUnit-based evaluation gates using Spring AI's `RelevancyEvaluator` and `FactCheckingEvaluator`.

## Planned implementation *(author's design, not from handbook)*
- Golden-set tests that fail the build when relevancy or fact-checking drops.
- The judge is a stub by default. Ollama runs as the judge under `-Pit`.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
