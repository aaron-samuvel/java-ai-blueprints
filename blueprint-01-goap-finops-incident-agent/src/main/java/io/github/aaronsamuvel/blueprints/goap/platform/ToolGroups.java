package io.github.aaronsamuvel.blueprints.goap.platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/** Named groups of Spring AI tools (for example {@code runbook-search}). */
public final class ToolGroups {

    private final Map<String, List<ToolCallback>> groups;

    public ToolGroups(Map<String, List<ToolCallback>> groups) {
        this.groups = Map.copyOf(groups);
    }

    public static ToolGroups of(String name, Object toolBean) {
        return new ToolGroups(Map.of(name, List.of(MethodToolCallbackProvider.builder().toolObjects(toolBean).build().getToolCallbacks())));
    }

    public static ToolGroups none() {
        return new ToolGroups(Map.of());
    }

    public List<ToolCallback> resolve(Collection<String> names) {
        List<ToolCallback> out = new ArrayList<>();
        for (String n : names) {
            List<ToolCallback> g = groups.get(n);
            if (g == null) throw new IllegalArgumentException("unknown tool group " + n);
            out.addAll(g);
        }
        return out;
    }
}
