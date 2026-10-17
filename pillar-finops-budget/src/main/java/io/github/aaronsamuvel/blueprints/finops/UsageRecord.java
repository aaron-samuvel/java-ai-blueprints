package io.github.aaronsamuvel.blueprints.finops;

import java.math.BigDecimal;

/** One settled model call: what was reserved, what was actually used, and where the number came from. */
public record UsageRecord(String tenant, long reserved, long promptTokens, long completionTokens,
                          boolean fromProvider, BigDecimal costInr) {
    public long totalTokens() {
        return promptTokens + completionTokens;
    }
}
