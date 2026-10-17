package io.github.aaronsamuvel.blueprints.mcp.tools;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Synthetic inventory data exposed as MCP tools by the "inventory" node of the mesh. */
public class InventoryTools {

    private static final Map<String, Integer> STOCK = Map.of("SKU-100", 42, "SKU-200", 0, "SKU-300", 7);

    @Tool(description = "Units in stock for a SKU")
    public String stockLevel(@ToolParam(description = "SKU, e.g. SKU-100") String sku) {
        Integer v = STOCK.get(sku);
        return v == null ? "unknown sku " + sku : sku + " in stock: " + v;
    }
}
