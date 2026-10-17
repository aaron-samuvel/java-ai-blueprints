package io.github.aaronsamuvel.blueprints.rag.handbook;

import java.util.List;

/** The handbook's answer contract: Markdown answer, its citations, and whether the model abstained. */
public record GroundedAnswer(String answerMarkdown, List<SourceCitation> citations, boolean abstainedDueToLowContext) {
    public GroundedAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
