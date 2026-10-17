# Blueprint 01: GOAP / OODA FinOps incident agent

**Status: done.** It has 10 offline tests, all passing (`mvn -B verify -pl blueprint-01-goap-finops-incident-agent -am`).

This agent watches a cloud bill and runs an **OODA loop** (Observe, Orient, Decide, Act). The Decide step is a **GOAP** planner: Goal-Oriented Action Planning, searched with A*. The agent re-plans on every cycle and runs only the first action of each plan, so new facts can change course. Examples of new facts are a denied approval or an unknown cause.

| Piece | File |
|---|---|
| Action model (preconditions, `unless` forbidden facts, effects, cost) | `planner/GoapAction.java` |
| A* planner | `planner/GoapPlanner.java` |
| OODA agent and the action catalogue | `ooda/FinOpsIncidentAgent.java` |
| HTTP API | `web/IncidentController.java` (`POST /api/incidents/run`, body `text/csv`) |

## Run it (Windows PowerShell)

```powershell
mvn -B -pl blueprint-01-goap-finops-incident-agent -am spring-boot:run
# in a second terminal:
curl.exe -X POST http://localhost:8081/api/incidents/run -H "Content-Type: text/csv" --data-binary "@blueprint-01-goap-finops-incident-agent/src/main/resources/billing-sample.csv"
```

The sample's `batch-etl` cost jumps from about 800 to 6400 INR. That is above 1.5× its baseline of 803.33, so the agent observes, diagnoses, plans and acts. It then returns an `IncidentReport` with the outcome and a trace of each cycle. By default it runs with the stub model, so no LLM is needed.

## Why not Embabel? (author's design, not from the handbook)

Embabel is the JVM GOAP agent framework the handbook points to. Its latest version on Maven Central is 1.5.3, and according to its POM it targets **Spring Boot 4.1.1** and Spring AI 2.0.1. This repo is pinned to Boot 4.0.8 everywhere, and the tests here have to be deterministic and offline. So the planner is a small, readable implementation (about 150 lines) of the same idea. The compatibility check was done **from POM metadata only**: Embabel was not run against Boot 4.0.8. See `theory.md` and `exercises.md` for porting to Embabel.

See also [`theory.md`](theory.md), [`examples/README.md`](examples/README.md) and [`exercises.md`](exercises.md).
