package io.github.aaronsamuvel.blueprints.rag.routing;

/** Decides which branch a query takes. Swap in an LLM classifier by implementing this. */
@FunctionalInterface
public interface QueryRouter {
    QueryIntent route(String query);
}
