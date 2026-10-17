# Version notes and handbook corrections

## Chosen versions (checked on Maven Central, 17 Oct 2026)
| Artifact | Latest seen | Chosen |
|---|---|---|
| `spring-boot-dependencies` | 4.1.1 (4.2.0 at M2) | **4.0.8** |
| `spring-ai-bom` | 2.0.1 (2.1.0 at M1) | **2.0.1** |
| JDK | Temurin 21.0.12.1 | **21** |
| Maven | 3.9.16 | 3.9.x |

### Compatibility check
- The handbook says "Spring AI 2.0". Spring AI 2.0.x GA is out and targets **Spring Boot 4**, so the handbook is right on this point.
- The Spring AI 2.0.1 autoconfigure POMs were built against Spring Boot **4.1.1**.
- We chose Boot **4.0.8** (the latest 4.0 patch) as the conservative option. We checked it with the full
  build: compile, Spring context start-up, MVC endpoint and `ChatClient` advisors all pass with 4.0.8.
- If a future module hits a Boot 4.1-only API, raise `spring-boot.version` to 4.1.x in the parent POM. That's a one-line change.
- The fallback (Boot 3.5.x + Spring AI 1.1.x) wasn't needed.

## Handbook claims corrected or qualified
| Handbook | What we do | Why |
|---|---|---|
| TOON gives 40–50% token savings | **Measured** in Blueprint 03: 38–39% vs compact JSON (65% vs pretty) on uniform tables; 16% on long-text chunks; nested data is 26–31% *larger* than compact JSON | Holds only for tabular payloads or a pretty-JSON baseline; see `blueprint-03-modular-rag/theory.md` |
| Azure Cosmos DB (multi-agent repo) | **pgvector** on Postgres | Free, local, and unified with business data (pillar 5) |
| Nacos (Spring AI Alibaba) | Plain Spring config / env vars | Too heavy for a learning repo; nothing here needs it |
| Docling as a running service | `DoclingStubParser` (pre-converted Markdown) | Python service not needed for the default build; HTTP client planned |
| Cloud-hosted models | **Ollama** (`llama3.2:3b`, `nomic-embed-text`) | No paid services; runs on an 8 GB PC |
| `thomasvitale/arconia` | `arconia-io/arconia` | The old path returns 404 |
