package io.github.aaronsamuvel.blueprints.finops;

/** Thrown before the model is called when a reservation would overrun the tenant's budget. */
public class BudgetExceededException extends RuntimeException {
    private final String tenant;
    private final long requested;
    private final long available;

    public BudgetExceededException(String tenant, long requested, long available) {
        super("token budget exceeded for tenant '%s': requested %d, available %d".formatted(tenant, requested, available));
        this.tenant = tenant;
        this.requested = requested;
        this.available = available;
    }

    public String tenant() { return tenant; }
    public long requested() { return requested; }
    public long available() { return available; }
}
