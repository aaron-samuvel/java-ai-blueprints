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
| `LifecycleDemoApplication.java` | ties them together with the stub model | — |
| [exercises.md](exercises.md) | practice tasks | — |

Code: `src/main/java/io/github/aaronsamuvel/blueprints/lifecycle/examples/`. See also [examples/README.md](examples/README.md).

## Run
```bash
mvn -B -pl part-1-lifecycle -am verify     # 4 tests, offline
```
