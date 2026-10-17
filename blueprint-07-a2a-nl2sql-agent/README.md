# Blueprint 07: A2A mesh + self-healing NL2SQL agent

**Status:** planned · **Handbook:** Part III, Blueprint 7

## Planned scope (from handbook notes)
- An agent-to-agent (A2A) mesh.
- A natural-language-to-SQL agent with a JSqlParser guard: SELECT only, `LIMIT 500` enforced, and up to 3 self-healing retries on SQL errors.

## Planned implementation *(author's design, not from handbook)*
- A JSqlParser validator that rejects DML/DDL and injects or caps `LIMIT 500`.
- A retry loop that feeds the DB error back to the model, at most 3 times.
- H2 for offline tests, Postgres under `-Pit`.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
