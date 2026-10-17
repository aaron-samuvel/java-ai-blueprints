# Part II: GitHub gems catalogue

**Status:** catalogue (links verified) · **Handbook:** Part II (10 repositories)

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
