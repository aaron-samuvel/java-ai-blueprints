package io.github.aaronsamuvel.blueprints.mcp.tools;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Synthetic billing data exposed as MCP tools by the "billing" node of the mesh. */
public class BillingTools {

    private static final Map<String, Integer> MONTH_TO_DATE_INR = Map.of("search-api", 61_500, "batch-etl", 32_800, "cache", 4_100);

    @Tool(description = "Month-to-date cloud cost in INR for a service")
    public String monthToDateCost(@ToolParam(description = "service name, e.g. search-api") String service) {
        Integer v = MONTH_TO_DATE_INR.get(service);
        return v == null ? "unknown service " + service : service + " month-to-date cost: INR " + v;
    }

    @Tool(description = "List services that have billing data")
    public String listServices() {
        return String.join(",", MONTH_TO_DATE_INR.keySet().stream().sorted().toList());
    }
}
