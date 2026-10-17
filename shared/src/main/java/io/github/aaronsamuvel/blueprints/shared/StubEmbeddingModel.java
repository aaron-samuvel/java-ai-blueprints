package io.github.aaronsamuvel.blueprints.shared;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic bag-of-words {@link EmbeddingModel}: each lower-cased word is hashed into one of
 * {@code dimensions} buckets and the vector is L2-normalised. Similar texts get similar vectors,
 * which is enough for offline RAG tests. Not a semantic model.
 */
public class StubEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public StubEmbeddingModel() {
        this(64);
    }

    public StubEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            out.add(new Embedding(vector(inputs.get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    public float[] vector(String text) {
        float[] v = new float[dimensions];
        if (text != null) {
            for (String w : text.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
                if (w.isEmpty()) continue;
                int h = 0;
                for (byte b : w.getBytes(StandardCharsets.UTF_8)) h = 31 * h + b;
                v[Math.floorMod(h, dimensions)] += 1f;
            }
        }
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        return v;
    }

    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot; // inputs are normalised
    }
}
