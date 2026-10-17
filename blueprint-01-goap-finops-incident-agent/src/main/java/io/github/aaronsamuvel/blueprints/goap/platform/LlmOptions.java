package io.github.aaronsamuvel.blueprints.goap.platform;

/**
 * Which model an LLM call uses and how. {@code role} picks a configured model ("fast" = cheap local
 * model for extraction, "frontier" = stronger model for synthesis); {@code model} optionally names the
 * provider model (for example {@code qwen2.5-coder:7b}); {@code temperature} null = provider default.
 */
public record LlmOptions(String role, String model, Double temperature) {

    public static final String FAST = "fast";
    public static final String FRONTIER = "frontier";

    public static LlmOptions fast() { return new LlmOptions(FAST, null, null); }

    public static LlmOptions frontier() { return new LlmOptions(FRONTIER, null, null); }

    public LlmOptions withTemperature(double t) { return new LlmOptions(role, model, t); }

    public LlmOptions withModel(String m) { return new LlmOptions(role, m, temperature); }
}
