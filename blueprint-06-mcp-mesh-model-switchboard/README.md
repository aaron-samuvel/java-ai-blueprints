# Blueprint 06: MCP server mesh and model switchboard (OAuth2)

**Status: done (see the handbook table below for partial items).** It has 23 offline tests, all passing (`mvn -B verify -pl blueprint-06-mcp-mesh-model-switchboard -am`).

* **MCP node:** a Spring AI 2.0.1 MCP server (`spring-ai-starter-mcp-server-webmvc`, Streamable HTTP at `/mcp`). It exposes one toolset, chosen with `blueprints.mcp.toolset=billing|inventory`.
* **Mesh:** `McpMesh` connects to N nodes with the MCP Java SDK 2.0.0 Streamable HTTP client. It sends a bearer token and merges every node's tools into Spring AI `ToolCallback`s that can go straight into `ChatClient.toolCallbacks(...)`.
* **OAuth2 resource server:** `/mcp/**` needs scope `mcp.tools`, `/api/switchboard/**` needs `models.use`, and everything else is denied.
* **Switchboard:** routes CHAT → LOCAL_FAST, ANALYSIS → LOCAL_SMART and CRITICAL → PREMIUM. PREMIUM requires the `models.premium` scope. Without it, the request is downgraded and the premium model is never called. If a tier is missing, the request falls back to a cheaper one.

## Handbook build (Part III, Blueprint 6, pp. 12–13)

| Handbook requirement | Where | Status |
|---|---|---|
| `spring-ai-starter-mcp-server-webmvc`, Streamable HTTP, secured by OAuth2 JWT | `application.properties` (`protocol=STREAMABLE`), `security/SecurityConfiguration` | Done |
| SSE transport as well | Spring AI serves one protocol per app. Setting `spring.ai.mcp.server.protocol=SSE` switches a node to SSE | Partial: one transport per node, and SSE is not covered by tests |
| a) `BatchIntrospectionMcpTools`: list executions, failed-step stack traces, guarded restart | `ops/BatchIntrospectionMcpTools` over Spring Batch 6 (JDBC job repository on H2) and the demo `ledgerReconciliationJob`. The restart needs the job on an allow-list, a FAILED execution that is the latest one, nothing running, a reason of 15+ characters, at most 3 restarts, and runs dry by default | Done |
| b) `JvmDiagnosticsMcpTools`: heap pools, GC pauses, virtual-thread pinned events, actuator health | `ops/JvmDiagnosticsMcpTools` and `JfrEventMonitor` (an in-process JFR `RecordingStream` for `jdk.GarbageCollection` and `jdk.VirtualThreadPinned`), plus the Boot `HealthEndpoint` | Done. The test really pins a virtual thread and sees the JFR event |
| c) `DynamicOpenApiMcpBridge`: register or update tools at runtime and broadcast tool-list changes | `ops/DynamicOpenApiMcpBridge`: turns an OpenAPI 3 document's GET operations into MCP tools through `McpSyncServer.addTool/removeTool` + `notifyToolsListChanged()`. Admin API `POST/DELETE /api/ops/openapi-bridge` needs scope `mcp.admin` | Done. The test checks that a connected client receives `tools/list_changed` and calls the bridged tool |
| `@McpTool` annotations | Tools use Spring AI `@Tool`, which the MCP server starter publishes | Substituted: `@Tool` is one of the two options the handbook lists |
| MCP resources and prompt templates | `ops/OpsMcpResourcesAndPrompts` (ops role): resources `ops://batch/jobs`, `ops://jvm/heap` and the template `ops://batch/executions/{executionId}`; prompts `diagnose-failed-batch-job` (argument `executionId`, embeds the failed-step details) and `jvm-health-review`. Published by Spring AI 2.0.1's `@McpResource` / `@McpPrompt` annotation scanner | Done. `OpsNodeTest.resourcesAndPromptsAreServedNextToTheTools` checks the capabilities, then lists and reads them over the secured Streamable HTTP endpoint |
| Separate modules `mcp-ops-server` / `ai-switchboard-host` | One Maven module. `MCP_TOOLSET=ops` runs the ops-server role, and `host/` is the switchboard host (it is also an MCP client through `mesh/McpMesh`) | Substituted: one deployable that plays both roles keeps the build small |
| `ModelSwitchboardRouter`: complexity, privacy, latency budget; local Ollama vs cloud | `host/ModelSwitchboardRouter`, `POST /api/switchboard/v2/route`. Weights and thresholds are the author's design | Done offline with stub models. Live Ollama and hosted-model wiring is not done (it needs paid API keys) |
| `StructuredOutputValidationAdvisor` with retry on validation errors | Spring AI 2.0.1's own advisor in `ModelSwitchboardRouter.extract(...)`, `POST /api/switchboard/v2/extract-ticket` | Done. The test sends truncated JSON, which leads to one retry with the error, then the valid record |
| Tests: MCP discovery, OAuth2 rejection, fallback to local Ollama on cloud timeout | `SecuredNodeTest`, `OpsNodeTest`, `ModelSwitchboardRouterTest` (300 ms timeout leads to the local fallback in under 2 s) | Done. "Local Ollama" is a stub offline |

Run the ops node: `MCP_TOOLSET=ops BLUEPRINTS_DEV_JWT_SECRET=... mvn -pl blueprint-06-mcp-mesh-model-switchboard -am spring-boot:run`.
`/actuator/health` is public and shows status only.

## Tokens and security (author's design, not from the handbook)

There is no external authorization server. For local development the node validates **HS256** JWTs signed with a secret from the env var `BLUEPRINTS_DEV_JWT_SECRET` (at least 32 bytes). If that variable is not set, no decoder is configured and the app will not start, which is intentional. Tests generate a random secret on every run with `SecureRandom` and mint tokens with Nimbus. No secret is committed. In production, replace the dev decoder with `spring.security.oauth2.resourceserver.jwt.issuer-uri` pointing at Keycloak, Entra ID or a similar provider.

## Run (Windows PowerShell)

```powershell
# 1. a random dev secret, kept only in this shell (32+ characters)
$env:BLUEPRINTS_DEV_JWT_SECRET = -join ((1..48) | % { [char](Get-Random -Min 65 -Max 90) })
# 2. start the node on port 8086 (override with $env:PORT)
mvn -B -pl blueprint-06-mcp-mesh-model-switchboard -am spring-boot:run
```

In a **second** shell, set the same `BLUEPRINTS_DEV_JWT_SECRET` value, then mint a one-hour token locally with
`DevJwtMinter` (it reads the secret from the env var only; arguments are the scopes) and call the switchboard:

```powershell
mvn -q -pl blueprint-06-mcp-mesh-model-switchboard -am install -DskipTests     # once
$token = mvn -q -pl blueprint-06-mcp-mesh-model-switchboard exec:java `
  "-Dexec.mainClass=io.github.aaronsamuvel.blueprints.mcp.security.DevJwtMinter" "-Dexec.args=models.use models.premium"
curl.exe -X POST http://localhost:8086/api/switchboard/ask -H "Authorization: Bearer $token" `
  -H "Content-Type: application/json" -d '{\"task\":\"CRITICAL\",\"message\":\"hi\"}'
```

Expected (stub models): `{"requested":"PREMIUM","used":"PREMIUM","downgraded":false,"answer":"[PREMIUM] hi"}`.
Mint with only `models.use` and the same request is downgraded to `LOCAL_SMART`; with no token you get `401`.
The MCP endpoint `/mcp` needs a token with scope `mcp.tools`.

Bash equivalent: `export BLUEPRINTS_DEV_JWT_SECRET=...`, `TOKEN=$(mvn -q ... exec:java ...)`, then
`curl -X POST localhost:8086/api/switchboard/ask -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"task":"CRITICAL","message":"hi"}'`.

See [`theory.md`](theory.md), [`examples/README.md`](examples/README.md) and [`exercises.md`](exercises.md).
