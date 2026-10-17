# Blueprint 04: Multi-agent transactional swarm with tenant-partitioned ChatMemory

**Status:** planned · **Handbook:** Part III, Blueprint 4

## Planned scope (from handbook notes)
- Several cooperating agents.
- Transactional consistency.
- ChatMemory partitioned per tenant.

## Planned implementation *(author's design, not from handbook)*
- JDBC ChatMemory on Postgres, with conversation ids prefixed by tenant.
- Agent state and business rows are written in one `@Transactional` boundary (pillar 5).
- Testcontainers Postgres under `-Pit`.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
