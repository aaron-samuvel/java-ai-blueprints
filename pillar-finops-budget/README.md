# Part IV pillar: FinOps reserve-call-commit budget advisor

**Status:** planned · **Handbook:** Part IV

## Planned scope (from handbook notes)
- A budget advisor using the reserve, call, commit pattern for token and cost control.

## Planned implementation *(author's design, not from handbook)*
- A `CallAdvisor` that reserves estimated tokens from a per-tenant budget, calls the model, then commits the actual usage from `ChatResponse` metadata (or releases the reservation on error).
- Rejects calls when the budget would be exceeded.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
