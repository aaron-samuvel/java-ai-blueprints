# Part I: J-AIDLC, five pillars, framework matrix

**Status:** done · **Handbook:** Part I

Covers the Java AI Development Lifecycle (J-AIDLC), the five Java-specific pillars and the
framework matrix. The code is small and runs offline. Each example backs up one idea from the theory.

| File | What it shows | Pillar / stage |
|---|---|---|
| [theory.md](theory.md) | 7 stages, 5 pillars, framework matrix | all |
| `examples/IncidentTriage.java` | record as a structured-output contract with validation | Stage 1, Pillar 2 |
| `examples/StructuredOutputExample.java` | `ChatClient…entity(IncidentTriage.class)` | Stage 1 |
| `examples/AuditTrailAdvisor.java` | minimal `CallAdvisor` around every model call | Pillar 3 |
| `examples/VirtualThreadFanOut.java` | parallel blocking calls on virtual threads | Pillar 1, Stage 7 |
| `observability/ObservedChatClients.java` | `ChatClient` wired to a Micrometer `ObservationRegistry` | Stage 6 |
| `observability/OpenInferenceObservationFilter.java` | adds `openinference.span.kind` (CHAIN, LLM, TOOL, RETRIEVER) to Spring AI observations | Stage 6 |
| `LifecycleDemoApplication.java` | ties them together with the stub model | — |
| [exercises.md](exercises.md) | practice tasks | — |

Code: `src/main/java/io/github/aaronsamuvel/blueprints/lifecycle/examples/`. See also [examples/README.md](examples/README.md).

## Run
```bash
mvn -B -pl part-1-lifecycle -am verify     # 6 tests, offline
# command-line demo (stub model, no server, no port)
mvn -B -pl part-1-lifecycle -am spring-boot:run
# or package and run the jar
mvn -B -pl part-1-lifecycle -am package -DskipTests
java -jar part-1-lifecycle/target/part-1-lifecycle-0.1.0-SNAPSHOT.jar
```
Output:
```
IncidentTriage[severity=HIGH, service=checkout, summary=p95 latency over 2s, nextSteps=[scale pods, check DB]]
[AuditEntry[promptChars=77, responseChars=110, millis=4]]
```
(`millis` varies.)

## Observability (Stage 6)
`ObservabilityTest` checks that one call through a `ChatClient` with a custom advisor emits a
`spring.ai.chat.client` observation and a `spring.ai.advisor` observation named `AuditTrailAdvisor`,
both tagged `openinference.span.kind=CHAIN`. The model, tool and vector-store names map to LLM, TOOL
and RETRIEVER.

To export the spans to the `otel-lgtm` service in `docker-compose.ai-support.yml`, add Spring Boot's OpenTelemetry
starter and set `management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:4318/v1/traces`.
That export path is **not run here** (no Docker on the build box), so Stage 6 is Partial in the
[traceability matrix](../docs/pdf-requirements-mapping.md).
