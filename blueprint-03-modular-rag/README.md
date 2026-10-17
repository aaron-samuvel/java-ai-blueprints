# Blueprint 03: Modular, branching, conditional RAG (Docling, PGVector)

**Status:** planned · **Handbook:** Part III, Blueprint 3

## Planned scope (from handbook notes)
- A modular RAG pipeline with branching and conditional routing.
- Docling for document parsing, PGVector for storage.

## Planned implementation *(author's design, not from handbook)*
- Spring AI `RetrievalAugmentationAdvisor`, with query routing by intent.
- Offline tests use `StubEmbeddingModel` with `SimpleVectorStore`. The `-Pit` tests use pgvector (Postgres on 15432) with Ollama `nomic-embed-text`.
- Docling runs as an optional local service.
- Includes TOON context serialisation, with *measured* token counts.

## Folder layout when built
`README.md`, `theory.md`, `examples/`, `exercises.md`, `pom.xml` + `src/main`, `src/test` (offline, stub models), `src/it` (`-Pit`).
