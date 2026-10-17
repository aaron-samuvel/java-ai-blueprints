package io.github.aaronsamuvel.blueprints.swarm.memory;

import java.util.function.Supplier;

/**
 * Current tenant for the executing thread. Set it at the edge (controller, message listener) with
 * {@link #callAs}; everything below reads it. No tenant means fail closed: access is denied.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static <T> T callAs(String tenant, Supplier<T> work) {
        if (tenant == null || !tenant.matches("[a-z0-9-]{1,40}")) {
            throw new IllegalArgumentException("invalid tenant id");
        }
        String previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static String require() {
        String t = CURRENT.get();
        if (t == null) throw new IllegalStateException("no tenant in context");
        return t;
    }
}
