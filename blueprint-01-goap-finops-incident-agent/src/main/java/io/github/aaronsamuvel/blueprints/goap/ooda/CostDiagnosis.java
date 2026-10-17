package io.github.aaronsamuvel.blueprints.goap.ooda;

/** Structured output of the LLM "orient" step. */
public record CostDiagnosis(Cause cause, String explanation) {
    public enum Cause { IDLE_RESOURCES, RUNAWAY_JOB, UNKNOWN }
}
