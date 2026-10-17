package io.github.aaronsamuvel.blueprints.swarm.commerce.domain;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** An order aggregate (Spring Data JDBC): the items are saved and loaded with it. */
@Table("customer_orders")
public record CustomerOrder(@Id Long id, String tenantId, String userId, String status, LocalDateTime createdAt,
                            @MappedCollection(idColumn = "order_id", keyColumn = "line_no") List<OrderItem> items) {

    public CustomerOrder withItems(List<OrderItem> newItems) {
        return new CustomerOrder(id, tenantId, userId, status, createdAt, newItems);
    }
}
