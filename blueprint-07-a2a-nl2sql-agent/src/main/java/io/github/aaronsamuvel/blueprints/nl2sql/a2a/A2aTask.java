package io.github.aaronsamuvel.blueprints.nl2sql.a2a;

import io.github.aaronsamuvel.blueprints.nl2sql.agent.Nl2SqlResult;

/** A2A-style task envelope: request and response. Simplified subset, not the full protocol. */
public final class A2aTask {
    private A2aTask() {}

    public record Request(String id, String skill, String message) {}

    public enum State { COMPLETED, FAILED }

    public record Response(String id, State state, Nl2SqlResult artifact) {}
}
