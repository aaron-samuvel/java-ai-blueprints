package io.github.aaronsamuvel.blueprints.rag.handbook;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Where a piece of the answer came from. Built from retrieval metadata, never invented by the model. */
public record SourceCitation(
        @JsonPropertyDescription("Original file name") String filename,
        @JsonPropertyDescription("Section heading, or 'page N' / 'part N' for PDFs and Office files") String section,
        @JsonPropertyDescription("1-based page number for PDFs, null otherwise") Integer page,
        @JsonPropertyDescription("Knowledge partition") String domainCategory,
        @JsonPropertyDescription("Date the source document takes effect") String effectiveDate,
        @JsonPropertyDescription("Similarity score of the chunk, 0..1") double score) {
}
