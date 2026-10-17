# Part II: GitHub gems catalogue

**Status:** done (catalogue + data module, 6 offline tests + 1 live link/licence IT) · **Handbook:** Part II (10 repositories)

Every link was checked against the GitHub REST API on **17 Oct 2026**. All of them returned HTTP 200.
The one-line descriptions are **author's summaries (not from handbook)**.

| # | Repository | Link check | What to study |
|---|---|---|---|
| 1 | [embabel/embabel-agent](https://github.com/embabel/embabel-agent) | ✅ 200 | GOAP agent framework for the JVM (Blueprint 01) |
| 2 | [langgraph4j/langgraph4j](https://github.com/langgraph4j/langgraph4j) | ✅ 200 (`bsorrentino/langgraph4j` redirects here) | Stateful agent graphs, HITL (Blueprint 02) |
| 3 | [JavaAIDev/agentic-patterns](https://github.com/JavaAIDev/agentic-patterns) | ✅ 200 | Agentic design patterns in Java |
| 4a | [thomasvitale/llm-apps-java-spring-ai](https://github.com/thomasvitale/llm-apps-java-spring-ai) | ✅ 200 | Spring AI sample apps |
| 4b | [arconia-io/arconia](https://github.com/arconia-io/arconia) | ✅ 200 (`thomasvitale/arconia` is **404**; the project lives under `arconia-io`) | Dev Services for Spring Boot |
| 5 | [ultramancode/spring-ai-privacy-guardrails](https://github.com/ultramancode/spring-ai-privacy-guardrails) | ✅ 200 | Privacy guardrails (compare with Blueprint 05) |
| 6 | [AzureCosmosDB/multi-agent-spring-ai](https://github.com/AzureCosmosDB/multi-agent-spring-ai) | ✅ 200 | Multi-agent Spring AI (Cosmos DB; we use pgvector instead) |
| 7 | [piomin/spring-ai-showcase](https://github.com/piomin/spring-ai-showcase) | ✅ 200 | Broad Spring AI feature tour |
| 8 | [iunera/ypipe](https://github.com/iunera/ypipe) | ✅ 200 | Pipeline project listed in the handbook |
| 9 | [alibaba/spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba) | ✅ 200 | Spring AI Alibaba, graph, JManus agent |
| 10 | [rajeshkawali/Springboot-ai-playground](https://github.com/rajeshkawali/Springboot-ai-playground) | ✅ 200 | Spring Boot AI playground examples |

Re-check the links yourself:
```bash
for r in embabel/embabel-agent langgraph4j/langgraph4j JavaAIDev/agentic-patterns; do
  curl -s -o /dev/null -w "$r %{http_code}\n" https://api.github.com/repos/$r; done
```

## Licences (checked 17 Oct 2026)

The GitHub API reports a licence for nine of the eleven repositories (Apache-2.0 or MIT). `iunera/ypipe` and
`rajeshkawali/Springboot-ai-playground` have **no licence detected**, so treat them as study material only: read
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

On the build box on 17 Oct 2026 the live check passed: all 11 repositories returned 200 and matched their recorded licence.
