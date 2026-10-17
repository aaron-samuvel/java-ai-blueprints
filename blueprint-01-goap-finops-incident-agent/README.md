# Blueprint 01: GOAP/OODA FinOps incident agent (Embabel)

**Status:** planned · **Handbook:** Part III, Blueprint 1

## Planned scope (from handbook notes)
- A goal-oriented action planning (GOAP) agent following an OODA loop (observe, orient, decide, act).
- Built on Embabel.
- Handles FinOps incidents (cost anomalies).

## Planned implementation *(author's design, not from handbook)*
- Embabel agent with typed actions: `DetectAnomaly`, `AttributeCost`, `ProposeRemediation`.
- Synthetic billing data, and a deterministic planner test using the stub model.
- Embabel's version must be checked against Spring Boot 4 first.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
