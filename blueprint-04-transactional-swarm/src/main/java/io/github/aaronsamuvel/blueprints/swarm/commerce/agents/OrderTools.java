package io.github.aaronsamuvel.blueprints.swarm.commerce.agents;

import java.time.LocalDateTime;
import java.util.List;

import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.OrderRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.ProductRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CustomerOrder;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.OrderItem;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.Product;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order agent's tools. Each is one database transaction: stock is decremented and the order
 * written together, or neither (an out-of-stock order rolls back). The tenant comes from the request
 * context, never from the model; the customer is bound per request with {@link #open}.
 */
public class OrderTools {

    public record OrderConfirmation(Long orderId, String sku, int quantity, String totalInr, String status) {}

    public record OrderStatus(Long orderId, String status, List<String> lines) {}

    private final ProductRepository products;
    private final OrderRepository orders;
    /** Per-request binding: the customer and a log of the tool calls made for them. */
    public record Session(String userId, List<String> calls) {}

    private static final ThreadLocal<Session> SESSION = new ThreadLocal<>();

    public OrderTools(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    /** Bind the customer for the tools on this thread; pair with {@link #close()}. */
    public static Session open(String userId) {
        Session s = new Session(userId, new java.util.concurrent.CopyOnWriteArrayList<>());
        SESSION.set(s);
        return s;
    }

    public static void close() {
        SESSION.remove();
    }

    private static Session session() {
        Session s = SESSION.get();
        if (s == null) throw new IllegalStateException("no customer bound for the order tools");
        return s;
    }

    private static String user() {
        return session().userId();
    }

    @Tool(name = "placeOrder", description = "Place an order for a product SKU for the current customer")
    @Transactional
    public OrderConfirmation placeOrder(@ToolParam(description = "Product SKU, for example SKU-HDP-01") String sku,
                                        @ToolParam(description = "Number of units, at least 1") int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("quantity must be at least 1");
        String tenant = TenantContext.require();
        Product p = products.findByTenantIdAndSku(tenant, sku)
                .orElseThrow(() -> new IllegalArgumentException("unknown SKU " + sku));
        if (p.stock() < quantity) throw new IllegalStateException("only " + p.stock() + " units of " + sku + " in stock");
        products.save(p.withStock(p.stock() - quantity));
        CustomerOrder saved = orders.save(new CustomerOrder(null, tenant, user(), "PLACED", LocalDateTime.now(),
                List.of(new OrderItem(null, sku, quantity, p.priceInr(), 0))));
        OrderConfirmation c = new OrderConfirmation(saved.id(), sku, quantity, p.priceInr().multiply(java.math.BigDecimal.valueOf(quantity)).toPlainString(), saved.status());
        session().calls().add("placeOrder(" + sku + ", " + quantity + ") -> order " + c.orderId());
        return c;
    }

    @Tool(name = "orderStatus", description = "Look up the status and lines of one of the current customer's orders")
    @Transactional(readOnly = true)
    public OrderStatus orderStatus(@ToolParam(description = "Order id") long orderId) {
        CustomerOrder o = orders.findByIdAndTenantId(orderId, TenantContext.require())
                .filter(x -> x.userId().equals(user()))
                .orElseThrow(() -> new IllegalArgumentException("order " + orderId + " not found for this customer"));
        session().calls().add("orderStatus(" + orderId + ") -> " + o.status());
        return new OrderStatus(o.id(), o.status(), o.items().stream()
                .map(i -> "item " + i.id() + ": " + i.quantity() + " x " + i.sku() + " @ INR " + i.unitPriceInr()).toList());
    }
}
