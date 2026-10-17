package io.github.aaronsamuvel.blueprints.mcp.host;

/** Where a task can run, cheapest and most private first. */
public enum ModelTarget {
    /** Local Ollama model (for example a 3B model): air-gapped, sovereign; also the fallback. */
    LOCAL_SOVEREIGN,
    /** Fast hosted model (for example Mistral or Groq) for extraction and tool arguments. */
    CLOUD_FAST,
    /** Frontier hosted model (for example OpenAI or Anthropic) for complex synthesis. */
    CLOUD_FRONTIER
}
