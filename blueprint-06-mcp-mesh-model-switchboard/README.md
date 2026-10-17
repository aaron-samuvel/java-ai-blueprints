# Blueprint 06: MCP server mesh and model switchboard (OAuth2)

**Status: done.** It has 9 offline tests, all passing (`mvn -B verify -pl blueprint-06-mcp-mesh-model-switchboard -am`).

* **MCP node:** a Spring AI 2.0.1 MCP server (`spring-ai-starter-mcp-server-webmvc`, Streamable HTTP at `/mcp`). It exposes one toolset, chosen with `blueprints.mcp.toolset=billing|inventory`.
* **Mesh:** `McpMesh` connects to N nodes with the MCP Java SDK 2.0.0 Streamable HTTP client. It sends a bearer token and merges every node's tools into Spring AI `ToolCallback`s that can go straight into `ChatClient.toolCallbacks(...)`.
* **OAuth2 resource server:** `/mcp/**` needs scope `mcp.tools`, `/api/switchboard/**` needs `models.use`, and everything else is denied.
* **Switchboard:** routes CHAT → LOCAL_FAST, ANALYSIS → LOCAL_SMART and CRITICAL → PREMIUM. PREMIUM requires the `models.premium` scope. Without it, the request is downgraded and the premium model is never called. If a tier is missing, the request falls back to a cheaper one.

## Tokens and security (author's design, not from the handbook)

There is no external authorization server. For local development the node validates **HS256** JWTs signed with a secret from the env var `BLUEPRINTS_DEV_JWT_SECRET` (at least 32 bytes). If that variable is not set, no decoder is configured and the app will not start, which is intentional. Tests generate a random secret on every run with `SecureRandom` and mint tokens with Nimbus. No secret is committed. In production, replace the dev decoder with `spring.security.oauth2.resourceserver.jwt.issuer-uri` pointing at Keycloak, Entra ID or a similar provider.

## Run (Windows PowerShell)

```powershell
$env:BLUEPRINTS_DEV_JWT_SECRET = -join ((1..48) | % { [char](Get-Random -Min 65 -Max 90) })
mvn -B -pl blueprint-06-mcp-mesh-model-switchboard -am spring-boot:run
```

Port 8086. To try the API by hand, mint a token with the same secret (for example on jwt.io, locally, with claim `"scope":"models.use"`). Then:
`curl.exe -X POST http://localhost:8086/api/switchboard/ask -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"task\":\"CRITICAL\",\"message\":\"hi\"}"`

See [`theory.md`](theory.md), [`examples/README.md`](examples/README.md) and [`exercises.md`](exercises.md).
