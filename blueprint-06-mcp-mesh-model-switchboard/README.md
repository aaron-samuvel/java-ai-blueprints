# Blueprint 06: MCP server mesh + model switchboard (OAuth2)

**Status:** planned · **Handbook:** Part III, Blueprint 6

## Planned scope (from handbook notes)
- A mesh of Model Context Protocol (MCP) servers.
- A model switchboard that routes requests between models.
- Secured with OAuth2.

## Planned implementation *(author's design, not from handbook)*
- Spring AI MCP server and client starters.
- A switchboard picks the Ollama model by task or cost.
- A Spring Security OAuth2 resource server, tested with mock JWTs.
- No external identity provider is needed for tests.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
