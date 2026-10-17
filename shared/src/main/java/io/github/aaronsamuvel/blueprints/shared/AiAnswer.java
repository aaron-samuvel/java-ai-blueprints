package io.github.aaronsamuvel.blueprints.shared;

import java.time.Duration;

/** Common result envelope used by blueprint REST endpoints. */
public record AiAnswer(String answer, String model, Duration latency) {
    public AiAnswer {
        if (answer == null) throw new IllegalArgumentException("answer must not be null");
    }
}
