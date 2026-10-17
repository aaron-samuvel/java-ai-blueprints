package io.github.aaronsamuvel.blueprints.swarm.commerce.domain;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** One line of an order. {@code refundedQuantity} caps what can still be refunded. */
@Table("order_items")
public record OrderItem(@Id Long id, String sku, int quantity, BigDecimal unitPriceInr, int refundedQuantity) {

    public OrderItem withRefunded(int more) {
        return new OrderItem(id, sku, quantity, unitPriceInr, refundedQuantity + more);
    }
}
