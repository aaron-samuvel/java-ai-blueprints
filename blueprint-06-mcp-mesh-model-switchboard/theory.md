# Theory

* **MCP** (Model Context Protocol) standardises how tools are described and called (`tools/list`, `tools/call` over JSON-RPC). With Streamable HTTP, a remote tool server is an ordinary HTTP endpoint. That means ordinary web security applies to it: bearer tokens, scopes and gateways.
* **Mesh:** split tools by domain into separate servers (billing, inventory). Each server can then be deployed, scaled and authorised on its own. The client merges them, and tool names are prefixed per server to avoid clashes.
* **Least privilege by scope:** tool access (`mcp.tools`) and model spend (`models.premium`) are separate grants. The switchboard turns an entitlement into a cost control: an unentitled CRITICAL request is still answered, but by a cheaper tier, and the response says it was `downgraded`.
* **Tested failure modes:** no token → 401, wrong scope → 403, expired token → 401, forged signature → 401.
