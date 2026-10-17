# Blueprint 02: LangGraph4j HITL multi-agent evaluation

**Status:** planned · **Handbook:** Part III, Blueprint 2

## Planned scope (from handbook notes)
- A LangGraph4j multi-agent graph with a human-in-the-loop (HITL) checkpoint.
- Used for evaluation workflows.

## Planned implementation *(author's design, not from handbook)*
- A graph of generator, critic and human-approval interrupt nodes, using an in-memory checkpoint saver.
- Tests resume the graph after a simulated approval.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
