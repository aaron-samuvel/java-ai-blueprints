package io.github.aaronsamuvel.blueprints.goap.platform;

import java.util.List;

/**
 * Record of one LLM call made through {@link Ai}: which action made it, with which options,
 * the exact prompt text and the names of the tools the model was allowed to see.
 * Tests assert on these, like Embabel's FakeOperationContext.
 */
public record LlmInvocation(String action, LlmOptions options, String prompt, List<String> toolNames, Class<?> outputType) {
    public LlmInvocation {
        toolNames = List.copyOf(toolNames);
    }
}
