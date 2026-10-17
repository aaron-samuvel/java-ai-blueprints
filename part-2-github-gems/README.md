# Part II: GitHub gems catalogue

**Status:** done (catalogue + data module, 6 offline tests + 1 live link/licence IT) · **Handbook:** Part II (10 rows, 15 repositories)

Every link was checked against the GitHub REST API on **17 Oct 2026**. All of them returned HTTP 200.
The one-line descriptions are **author's summaries (not from handbook)**.

| # | Repository | Link check | What to study |
|---|---|---|---|
| 1a | [embabel/embabel-agent](https://github.com/embabel/embabel-agent) | ✅ 200 | GOAP agent framework for the JVM (Blueprint 01) |
| 1b | [embabel/java-agent-template](https://github.com/embabel/java-agent-template) | ✅ 200 | Embabel starter with `FakeOperationContext` tests (Blueprint 01) |
| 2a | [langgraph4j/langgraph4j](https://github.com/langgraph4j/langgraph4j) | ✅ 200 (`bsorrentino/langgraph4j` redirects here) | Stateful agent graphs, HITL (Blueprint 02) |
| 2b | [ykpraveen/langchain4j-sample](https://github.com/ykpraveen/langchain4j-sample) | ✅ 200 | Parallel hiring fan-out with pause/resume (Blueprint 02) |
| 3 | [JavaAIDev/agentic-patterns](https://github.com/JavaAIDev/agentic-patterns) | ✅ 200 | Five agentic workflow patterns (Blueprint 04) |
| 4a | [thomasvitale/llm-apps-java-spring-ai](https://github.com/thomasvitale/llm-apps-java-spring-ai) | ✅ 200 | Spring AI sample apps, modular RAG (Blueprint 03) |
| 4b | [arconia-io/arconia](https://github.com/arconia-io/arconia) | ✅ 200 (`thomasvitale/arconia` is **404**; the project lives under `arconia-io`) | Dev Services for Spring Boot (Part I) |
| 5 | [ultramancode/spring-ai-privacy-guardrails](https://github.com/ultramancode/spring-ai-privacy-guardrails) | ✅ 200 | Privacy guardrails (compare with Blueprint 05) |
| 6 | [AzureCosmosDB/multi-agent-spring-ai](https://github.com/AzureCosmosDB/multi-agent-spring-ai) | ✅ 200 | Multi-agent Spring AI (Cosmos DB; Blueprint 04 uses SQL instead) |
| 7 | [piomin/spring-ai-showcase](https://github.com/piomin/spring-ai-showcase) | ✅ 200 | Multi-provider profiles, structured-output validation (Blueprint 06) |
| 8 | [iunera/ypipe](https://github.com/iunera/ypipe) | ✅ 200 | Local "model switchboard" and MCP orchestration (Blueprint 06) |
| 9a | [alibaba/spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba) | ✅ 200 | Graph runtime, A2A, DataAgent NL2SQL (Blueprint 07) |
| 9b | [rainerWJY/JManus](https://github.com/rainerWJY/JManus) | ✅ 200 | JManus agent (Blueprint 07) |
| 10a | [rajeshkawali/Springboot-ai-playground](https://github.com/rajeshkawali/Springboot-ai-playground) | ✅ 200 | Spring AI vs LangChain4j side by side |
| 10b | [anmiralles/springboot-ai-rag-docingest](https://github.com/anmiralles/springboot-ai-rag-docingest) | ✅ 200 | Async document ingestion (Blueprint 03). The table names only `springboot-ai-rag-docingest`; the owner comes from Blueprint 03's reference line |

The handbook's table has 10 rows. Five of them (1, 2, 4, 9, 10) name two repositories, so the catalogue
has 15 entries, numbered `1a`/`1b` and so on.

Re-check the links yourself:
```bash
for r in embabel/embabel-agent langgraph4j/langgraph4j JavaAIDev/agentic-patterns; do
  curl -s -o /dev/null -w "$r %{http_code}\n" https://api.github.com/repos/$r; done
```

## Licences (checked 17 Oct 2026)

The GitHub API reports a licence for eleven of the fifteen repositories (Apache-2.0 or MIT). `iunera/ypipe`,
`rajeshkawali/Springboot-ai-playground`, `ykpraveen/langchain4j-sample` and `anmiralles/springboot-ai-rag-docingest` have **no licence detected**, so treat them as study material only: read
them, but don't copy their code. `java -jar` on this module prints the full table with reuse advice (see below).

## What's in this folder

| File | What it is |
|---|---|
| [theory.md](theory.md) | How to study an open-source AI repo: what to look for and how each gem maps to the handbook |
| [examples/README.md](examples/README.md) | Cloning a gem next to this repo and comparing it with the matching blueprint |
| [exercises.md](exercises.md) | Practice tasks |
| `src/main/resources/gems.csv` | The catalogue as data: number, repo, licence, language, related folder |
| `src/main/java/.../gems/` | `Gem`, `GemsCatalogue`, `ReuseAdvice`, and the `GemsReport` CLI (the project) |
| `src/test/java/.../GemsCatalogueTest` | 6 offline tests: 10 handbook entries, related folders exist, README and CSV agree, licence advice |
| `src/it/java/.../GitHubCatalogueIT` | Live (`-Pit`): every repo returns HTTP 200 from the GitHub API and still has the recorded licence |

## Run

```bash
mvn -B -pl part-2-github-gems -am verify            # 6 offline tests
mvn -B -pl part-2-github-gems -am spring-boot:run   # prints the catalogue table (CLI, no port)
mvn -B -Pit -pl part-2-github-gems verify           # live GitHub API check (needs internet, no token)
```

On the build box on 17 Oct 2026 the live check passed: all 15 repositories returned 200 and matched their recorded licence (re-run after the catalogue grew from 11 to 15 entries).
