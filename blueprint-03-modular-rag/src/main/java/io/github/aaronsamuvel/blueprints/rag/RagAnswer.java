package io.github.aaronsamuvel.blueprints.rag;

import java.util.List;

import io.github.aaronsamuvel.blueprints.rag.routing.QueryIntent;

/** Answer plus which branch was taken and which sources grounded it. */
public record RagAnswer(String answer, QueryIntent intent, List<String> sources) {
    public RagAnswer {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
