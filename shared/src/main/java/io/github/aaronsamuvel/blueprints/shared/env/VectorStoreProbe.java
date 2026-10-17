package io.github.aaronsamuvel.blueprints.shared.env;

import java.util.function.Supplier;

import io.github.aaronsamuvel.blueprints.shared.env.EnvironmentProbe.ProbeResult;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Vector store probe: one top-1 similarity search. Lives in its own class so apps without
 * spring-ai-vector-store on the classpath never load it. Register it as an {@link EnvironmentProbe} bean.
 */
public final class VectorStoreProbe implements EnvironmentProbe {

    private final Supplier<VectorStore> store;

    public VectorStoreProbe(Supplier<VectorStore> store) {
        this.store = store;
    }

    @Override
    public ProbeResult probe() {
        VectorStore s = store.get();
        if (s == null) return ProbeResult.notConfigured("vectorStore", "no VectorStore bean");
        try {
            int hits = s.similaritySearch(SearchRequest.builder().query("ping").topK(1).build()).size();
            return ProbeResult.up("vectorStore", s.getClass().getSimpleName() + ", search ok (" + hits + " hit)");
        } catch (RuntimeException e) {
            return ProbeResult.down("vectorStore", s.getClass().getSimpleName() + ": " + EnvironmentProbes.firstLine(e));
        }
    }
}
