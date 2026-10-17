package io.github.aaronsamuvel.blueprints.pii.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-conversation, in-memory, reversible mapping between raw PII values and opaque tokens such
 * as {@code <<EMAIL_1>>}. The vault never leaves the JVM; only tokens are sent to the model.
 */
public final class TokenVault {

    private final Map<String, String> valueToToken = new LinkedHashMap<>();
    private final Map<String, String> tokenToValue = new LinkedHashMap<>();
    private final Map<PiiType, AtomicInteger> counters = new EnumMap<>(PiiType.class);

    public synchronized String tokenFor(PiiType type, String value) {
        return valueToToken.computeIfAbsent(value, v -> {
            int n = counters.computeIfAbsent(type, t -> new AtomicInteger()).incrementAndGet();
            String token = "<<" + type.name() + "_" + n + ">>";
            tokenToValue.put(token, v);
            return token;
        });
    }

    public synchronized String valueFor(String token) {
        return tokenToValue.get(token);
    }

    public synchronized Map<String, String> tokens() {
        return Map.copyOf(tokenToValue);
    }

    public synchronized int size() {
        return tokenToValue.size();
    }
}
