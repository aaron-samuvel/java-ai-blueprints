package io.github.aaronsamuvel.blueprints.finops;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Estimates tokens with a real BPE tokenizer (cl100k_base by default). For Llama-family
 * models it is an approximation, which is why reservations add a safety margin.
 */
public class TokenEstimator {

    private final Encoding encoding;
    private final double safetyMargin;

    public TokenEstimator() {
        this(EncodingType.CL100K_BASE, 1.2);
    }

    public TokenEstimator(EncodingType type, double safetyMargin) {
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(type);
        this.safetyMargin = safetyMargin;
    }

    public int count(String text) {
        return text == null ? 0 : encoding.countTokens(text);
    }

    public long withMargin(String text) {
        return (long) Math.ceil(count(text) * safetyMargin);
    }
}
