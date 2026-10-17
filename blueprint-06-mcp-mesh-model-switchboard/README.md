# Blueprint 06: MCP server mesh and model switchboard (OAuth2)

**Status: done.** It has 13 offline tests, all passing (`mvn -B verify -pl blueprint-06-mcp-mesh-model-switchboard -am`).

* **MCP node:** a Spring AI 2.0.1 MCP server (`spring-ai-starter-mcp-server-webmvc`, Streamable HTTP at `/mcp`). It exposes one toolset, chosen with `blueprints.mcp.toolset=billing|inventory`.
* **Mesh:** `McpMesh` connects to N nodes with the MCP Java SDK 2.0.0 Streamable HTTP client. It sends a bearer token and merges every node's tools into Spring AI `ToolCallback`s that can go straight into `ChatClient.toolCallbacks(...)`.
* **OAuth2 resource server:** `/mcp/**` needs scope `mcp.tools`, `/api/switchboard/**` needs `models.use`, and everything else is denied.
* **Switchboard:** routes CHAT → LOCAL_FAST, ANALYSIS → LOCAL_SMART and CRITICAL → PREMIUM. PREMIUM requires the `models.premium` scope. Without it, the request is downgraded and the premium model is never called. If a tier is missing, the request falls back to a cheaper one.

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
