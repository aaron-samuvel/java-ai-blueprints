package io.github.aaronsamuvel.blueprints.goap.platform;

/**
 * The LLM handle an {@link Action} receives. Every call is recorded as an {@link LlmInvocation}
 * and may only use the tools of the action's {@code toolGroups}.
 */
public interface Ai {

    Ai withLlm(LlmOptions options);

    /** Ask the model for a typed object (structured output into a record). */
    <T> T createObject(String prompt, Class<T> type);
}
