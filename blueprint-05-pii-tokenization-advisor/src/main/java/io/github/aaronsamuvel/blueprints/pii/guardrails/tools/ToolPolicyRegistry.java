package io.github.aaronsamuvel.blueprints.pii.guardrails.tools;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.support.AopUtils;

/**
 * Tool name to {@link ToolPolicy}. Annotated {@code @Tool} beans register themselves; tools that
 * have no Java method here (for example tools from an MCP server, which arrive as plain
 * {@code ToolCallback}s) are registered by name. Unknown tools get {@link ToolPolicy#NONE}.
 */
public final class ToolPolicyRegistry {

    private final Map<String, ToolPolicy> policies = new ConcurrentHashMap<>();

    public ToolPolicyRegistry registerAnnotated(Object... toolBeans) {
        for (Object bean : toolBeans) {
            for (Method m : AopUtils.getTargetClass(bean).getMethods()) {
                Tool tool = m.getAnnotation(Tool.class);
                if (tool == null) continue;
                String name = tool.name() == null || tool.name().isBlank() ? m.getName() : tool.name();
                ToolPrivacyPolicy p = m.getAnnotation(ToolPrivacyPolicy.class);
                policies.put(name, p == null ? ToolPolicy.NONE : new ToolPolicy(Set.of(p.allowedPiiTypes()), p.requiredRole()));
            }
        }
        return this;
    }

    public ToolPolicyRegistry register(String toolName, ToolPolicy policy) {
        policies.put(toolName, policy);
        return this;
    }

    public ToolPolicy policyFor(String toolName) {
        return policies.getOrDefault(toolName, ToolPolicy.NONE);
    }
}
