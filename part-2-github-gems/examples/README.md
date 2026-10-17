# Examples

## Print the catalogue with reuse advice
```bash
mvn -B -pl part-2-github-gems -am package -DskipTests
java -jar part-2-github-gems/target/part-2-github-gems-0.1.0-SNAPSHOT.jar
```
Output (first rows, recorded on the build box on 17 Oct 2026):
```
Part II: 10 handbook entries, 15 repositories (licences as reported by GitHub on 2026-10-17)

| # | Repository | Licence | Reuse | Compare with |
|---|---|---|---|---|
| 1 | [embabel/embabel-agent](https://github.com/embabel/embabel-agent) | Apache-2.0 | copy allowed with attribution and the licence notice | `blueprint-01-goap-finops-incident-agent` |
...
| 8 | [iunera/ypipe](https://github.com/iunera/ypipe) | none | no licence detected: study only, do not copy code | `blueprint-06-mcp-mesh-model-switchboard` |
```

## Compare a gem with its blueprint
```bash
# next to this repo, not inside it
git clone --depth 1 https://github.com/ultramancode/spring-ai-privacy-guardrails ../gems/privacy-guardrails
# where does each project intercept model calls?
rg -l "CallAdvisor|BaseAdvisor" ../gems/privacy-guardrails blueprint-05-pii-tokenization-advisor/src/main
```
Then walk the five-question checklist in [theory.md](../theory.md) for both and note the differences.

## Use the catalogue from code
```java
GemsCatalogue catalogue = GemsCatalogue.load();
catalogue.relatedTo("blueprint-02-langgraph4j-hitl-evaluation");   // langgraph4j, spring-ai-alibaba
catalogue.gems().stream().filter(g -> g.reuseAdvice() == ReuseAdvice.READ_ONLY).toList();
```
