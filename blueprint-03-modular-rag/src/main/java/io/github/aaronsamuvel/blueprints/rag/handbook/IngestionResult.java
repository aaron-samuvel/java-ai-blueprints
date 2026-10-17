package io.github.aaronsamuvel.blueprints.rag.handbook;

/** Outcome of one ingest call. {@code UNCHANGED} means the content hash was already indexed. */
public record IngestionResult(String filename, String contentHash, Status status, int chunks, String reader) {
    public enum Status { NEW, REPLACED, UNCHANGED }
}
