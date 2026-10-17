package io.github.aaronsamuvel.blueprints.mcp.ops;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Turns the read-only ({@code GET}) operations of an OpenAPI 3 document into MCP tools at runtime,
 * and broadcasts {@code notifications/tools/list_changed} to connected clients (handbook Part III,
 * Blueprint 6, "DynamicOpenApiMcpBridge"). Registering the same source again replaces its tools.
 * Tool names are {@code <source>_<operationId>}. Non-GET operations are skipped on purpose.
 */
public class DynamicOpenApiMcpBridge {

    public record Registration(String source, String baseUrl, List<String> registered, List<String> skipped, List<String> removed) {}

    private final McpSyncServer server;
    private final RestClient http = RestClient.create();
    private final JsonMapper json = JsonMapper.builder().build();
    private final Map<String, Set<String>> toolsBySource = new ConcurrentHashMap<>();

    public DynamicOpenApiMcpBridge(McpSyncServer server) {
        this.server = server;
    }

    public synchronized Registration register(String source, String openApiJson, String baseUrl) {
        if (source == null || !source.matches("[a-z][a-z0-9-]{1,30}")) throw new IllegalArgumentException("source must match [a-z][a-z0-9-]{1,30}");
        URI base = URI.create(baseUrl);
        if (!"http".equals(base.getScheme()) && !"https".equals(base.getScheme())) throw new IllegalArgumentException("baseUrl must be http(s)");
        JsonNode doc = json.readTree(openApiJson);
        if (!doc.path("openapi").asString("").startsWith("3.")) throw new IllegalArgumentException("only OpenAPI 3.x documents are supported");

        List<String> registered = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, McpServerFeatures.SyncToolSpecification> specs = new LinkedHashMap<>();
        for (var path : doc.path("paths").properties()) {
            for (var op : path.getValue().properties()) {
                String method = op.getKey();
                String opId = op.getValue().path("operationId").asString("");
                if (!"get".equals(method)) {
                    skipped.add(method.toUpperCase() + " " + path.getKey() + " (only GET is bridged)");
                    continue;
                }
                if (opId.isBlank()) {
                    skipped.add("GET " + path.getKey() + " (no operationId)");
                    continue;
                }
                String name = source.replace('-', '_') + "_" + opId;
                specs.put(name, spec(name, path.getKey(), op.getValue(), baseUrl));
            }
        }
        Set<String> previous = toolsBySource.getOrDefault(source, Set.of());
        List<String> removed = new ArrayList<>();
        for (String old : previous) {
            server.removeTool(old);
            if (!specs.containsKey(old)) removed.add(old);
        }
        specs.forEach((name, spec) -> {
            server.addTool(spec);
            registered.add(name);
        });
        toolsBySource.put(source, Set.copyOf(specs.keySet()));
        server.notifyToolsListChanged();
        return new Registration(source, baseUrl, registered, skipped, removed);
    }

    public synchronized List<String> unregister(String source) {
        Set<String> names = toolsBySource.remove(source);
        if (names == null) return List.of();
        names.forEach(server::removeTool);
        server.notifyToolsListChanged();
        return List.copyOf(names);
    }

    private McpServerFeatures.SyncToolSpecification spec(String name, String pathTemplate, JsonNode op, String baseUrl) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        List<String> pathParams = new ArrayList<>();
        List<String> queryParams = new ArrayList<>();
        for (JsonNode p : op.path("parameters")) {
            String pName = p.path("name").asString();
            String in = p.path("in").asString();
            if (!"path".equals(in) && !"query".equals(in)) continue;
            (("path".equals(in)) ? pathParams : queryParams).add(pName);
            props.put(pName, Map.of("type", p.path("schema").path("type").asString("string"),
                    "description", p.path("description").asString(in + " parameter")));
            if (p.path("required").asBoolean(false) || "path".equals(in)) required.add(pName);
        }
        String description = op.path("summary").asString(op.path("description").asString("GET " + pathTemplate));
        McpSchema.Tool tool = McpSchema.Tool.builder().name(name).description(description + " (bridged from OpenAPI, GET " + pathTemplate + ")")
                .inputSchema(new McpSchema.JsonSchema("object", props, required, false, null, null)).build();
        return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange, req) -> {
            Map<String, Object> args = req.arguments() == null ? Map.of() : req.arguments();
            for (String r : required) {
                if (!args.containsKey(r)) return McpSchema.CallToolResult.builder().isError(true).addTextContent("missing argument " + r).build();
            }
            Map<String, Object> pathValues = new LinkedHashMap<>();
            pathParams.forEach(p -> pathValues.put(p, String.valueOf(args.get(p))));
            UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(baseUrl.replaceAll("/+$", "") + pathTemplate);
            queryParams.forEach(q -> { if (args.containsKey(q)) uri.queryParam(q, args.get(q)); });
            try {
                String body = http.get().uri(uri.buildAndExpand(pathValues).encode().toUri()).retrieve().body(String.class);
                return McpSchema.CallToolResult.builder().addTextContent(body == null ? "" : body).build();
            } catch (RestClientResponseException e) {
                return McpSchema.CallToolResult.builder().isError(true).addTextContent("HTTP " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString()).build();
            }
        }).build();
    }
}
