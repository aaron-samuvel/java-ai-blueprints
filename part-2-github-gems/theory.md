# Part II theory: learning from open-source Java AI repositories

> The handbook's Part II table (pages 4-5) has ten rows naming fifteen repositories. The "compare with" folder for
> each one is the blueprint whose handbook text cites it. The study method below is **author's design (not from handbook)**.

## Why study other repositories
The blueprints in this repo are deliberately small and offline. Real projects show what happens next: how
people wire a real identity provider, swap vector stores, package local models, or structure many agents. Reading
two implementations of the same idea, ours and theirs, is the fastest way to see which parts are essential and
which are taste.

## A five-question checklist for any AI repo
1. **Contract:** where are the model's inputs and outputs typed (records, JSON schema, tool signatures)? Untyped
   strings everywhere are a smell (Part I, pillar "records as LLM contracts").
2. **Interception point:** is there one place every model call passes through (Spring AI advisors, LangChain4j
   listeners, a graph node)? That is where guardrails, budgets and auditing belong (compare Blueprint 05 and the
   FinOps pillar).
3. **State:** where does conversation or agent state live, and is it transactional with business data? (Blueprint 04.)
4. **Tests without a model:** can the build pass offline? Look for stub or mock models, recorded responses and
   evaluation sets (compare `shared/` and the eval-gates pillar).
5. **Licence:** may you copy code, or only learn from it? See below.

## Licences matter before you copy
This repository is MIT-licensed. Code copied from an Apache-2.0 or MIT project may be reused with attribution and the
licence notice. Code from a repository with **no licence** is all-rights-reserved by default, even when it is public:
read it, learn the idea, and write your own version. Copyleft licences (GPL family) bring obligations that don't fit
an MIT repository without care. `ReuseAdvice` encodes exactly this rule from the SPDX id that GitHub reports; it's a
study aid, not legal advice, so always open the actual LICENSE file.

## Mapping the gems to this repository
`gems.csv` records, for each gem, the folder here it is most useful to compare with:

| Gem | Compare with | Why (handbook's "core innovation" column, paraphrased) |
|---|---|---|
| embabel/embabel-agent, embabel/java-agent-template | Blueprint 01 | GOAP planning separated from LLM calls; `FakeOperationContext` tests |
| langgraph4j/langgraph4j, ykpraveen/langchain4j-sample | Blueprint 02 | Stateful cyclic graphs, reducers, HITL pause/resume |
| JavaAIDev/agentic-patterns | Blueprint 04 | Chain, parallel, routing, orchestrator-workers, evaluator-optimizer |
| thomasvitale/llm-apps-java-spring-ai | Blueprint 03 | Modular, branching, conditional RAG |
| arconia-io/arconia | Part I | Dev Services and OpenInference: the "hermetic local prototyping" and observability stages |
| ultramancode/spring-ai-privacy-guardrails | Blueprint 05 | Request-scoped PII tokenisation and least-privilege tools |
| AzureCosmosDB/multi-agent-spring-ai | Blueprint 04 | Swarm handoffs, vector search and ACID tools in one app |
| piomin/spring-ai-showcase, iunera/ypipe | Blueprint 06 | Multi-provider switching, structured-output validation, local switchboard plus MCP |
| alibaba/spring-ai-alibaba, rainerWJY/JManus | Blueprint 07 | A2A mesh, graph runtime, NL2SQL DataAgent |
| anmiralles/springboot-ai-rag-docingest | Blueprint 03 | Asynchronous document ingestion |
| rajeshkawali/Springboot-ai-playground | — | Spring AI vs LangChain4j comparison; useful across all modules |

The links, licences and languages were checked against the GitHub API on 17 Oct 2026.

## Keeping a catalogue honest
Links rot and licences change. The catalogue is data (`gems.csv`), and two kinds of test keep it honest:
offline tests check that it is internally consistent with this repository (the README lists the same repos, every
"compare with" folder exists), and a live test (`GitHubCatalogueIT`, `-Pit`) checks it against GitHub itself.
