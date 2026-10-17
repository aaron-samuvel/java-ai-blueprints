package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

/** A workflow hit its iteration or tool-call cap. */
public class LoopGuardException extends RuntimeException {
    public LoopGuardException(String message) {
        super(message);
    }
}
