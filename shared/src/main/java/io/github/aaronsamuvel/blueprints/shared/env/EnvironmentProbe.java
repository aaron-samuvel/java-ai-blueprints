package io.github.aaronsamuvel.blueprints.shared.env;

/**
 * One check of the supporting environment (a model, a vector store, an external service).
 * Implementations must be cheap and must not spend tokens: they never call a paid chat model.
 */
@FunctionalInterface
public interface EnvironmentProbe {

    ProbeResult probe();

    /** Outcome of one probe. NOT_CONFIGURED never makes the overall health DOWN. */
    record ProbeResult(String component, State state, String detail) {
        public enum State { UP, DOWN, NOT_CONFIGURED }

        public static ProbeResult up(String c, String d) { return new ProbeResult(c, State.UP, d); }
        public static ProbeResult down(String c, String d) { return new ProbeResult(c, State.DOWN, d); }
        public static ProbeResult notConfigured(String c, String d) { return new ProbeResult(c, State.NOT_CONFIGURED, d); }
    }
}
