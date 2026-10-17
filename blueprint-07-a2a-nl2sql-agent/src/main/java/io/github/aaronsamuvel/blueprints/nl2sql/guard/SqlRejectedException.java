package io.github.aaronsamuvel.blueprints.nl2sql.guard;

/** The guard refused the SQL. The message is fed back to the model on retry. */
public class SqlRejectedException extends RuntimeException {
    public SqlRejectedException(String message) {
        super(message);
    }
}
