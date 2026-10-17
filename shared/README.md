# shared: offline test doubles

Support code, not a handbook topic. Every module depends on it so that `mvn -B verify` needs no network, Docker or LLM.

| Class | What it does |
|---|---|
| `StubChatModel` | Deterministic Spring AI `ChatModel`: `echo()` returns the prompt as `echo: …`; `fixed(answer)` always returns one reply |
| `StubEmbeddingModel` | Deterministic bag-of-words `EmbeddingModel` (each word hashed into a fixed-size vector), so vector-store code runs offline |
| `AiAnswer` | Common answer record used by the sample endpoints |
| `StubModelConfiguration` | Spring configuration that registers the stub `ChatModel` when `blueprints.model=stub` (the default) and no other `ChatModel` exists |

Tests: `StubModelsTest` (2). Run with `mvn -B -pl shared verify`.

Don't copy these classes into modules; depend on `shared` instead (see [AGENTS.md](../AGENTS.md)).
