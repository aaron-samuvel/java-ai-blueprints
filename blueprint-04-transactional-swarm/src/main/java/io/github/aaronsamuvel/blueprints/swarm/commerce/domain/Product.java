package io.github.aaronsamuvel.blueprints.swarm.commerce.domain;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

/** A catalogue entry, partitioned by tenant. {@code version} gives optimistic locking on stock updates. */
@Table("products")
public record Product(@Id Long id, String tenantId, String sku, String name, String description, String category,
                      BigDecimal priceInr, int stock, @Version Integer version) {

    public Product withStock(int newStock) {
        return new Product(id, tenantId, sku, name, description, category, priceInr, newStock, version);
    }
}
