package io.github.aaronsamuvel.blueprints.eval;

import java.util.List;

/** One golden-set row: question, retrieved context, the system's answer. */
public record EvalCase(String id, String question, List<String> context, String answer) {
    public EvalCase {
        context = List.copyOf(context);
    }
}
