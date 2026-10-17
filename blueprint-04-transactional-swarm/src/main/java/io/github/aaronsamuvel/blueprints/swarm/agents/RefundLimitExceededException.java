package io.github.aaronsamuvel.blueprints.swarm.agents;

/** Business rule violation. Unchecked, so Spring rolls the whole swarm transaction back. */
public class RefundLimitExceededException extends RuntimeException {
    public RefundLimitExceededException(String message) {
        super(message);
    }
}
