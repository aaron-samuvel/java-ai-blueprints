package io.github.aaronsamuvel.blueprints.finops;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Price per 1,000 tokens in INR. {@link #FREE} for local Ollama, which is the default here. */
public record PriceList(BigDecimal inrPer1kPrompt, BigDecimal inrPer1kCompletion) {
    public static final PriceList FREE = new PriceList(BigDecimal.ZERO, BigDecimal.ZERO);

    public BigDecimal cost(long promptTokens, long completionTokens) {
        return inrPer1kPrompt.multiply(BigDecimal.valueOf(promptTokens))
                .add(inrPer1kCompletion.multiply(BigDecimal.valueOf(completionTokens)))
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
    }
}
