package io.github.aaronsamuvel.blueprints.swarm.commerce.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;

/** Spring Data JDBC repositories. Every finder takes the tenant explicitly. */
public final class CommerceRepositories {

    private CommerceRepositories() {}

    public interface ProductRepository extends ListCrudRepository<Product, Long> {
        Optional<Product> findByTenantIdAndSku(String tenantId, String sku);

        List<Product> findByTenantId(String tenantId);

        @Query("SELECT * FROM products WHERE tenant_id = :tenant AND (LOWER(name) LIKE :pattern OR LOWER(description) LIKE :pattern OR LOWER(category) LIKE :pattern)")
        List<Product> keywordSearch(@Param("tenant") String tenantId, @Param("pattern") String likePattern);
    }

    public interface OrderRepository extends ListCrudRepository<CustomerOrder, Long> {
        List<CustomerOrder> findByTenantIdAndUserId(String tenantId, String userId);

        Optional<CustomerOrder> findByIdAndTenantId(Long id, String tenantId);

        @Query("SELECT o.* FROM customer_orders o JOIN order_items i ON i.order_id = o.id WHERE i.id = :itemId AND o.tenant_id = :tenant")
        Optional<CustomerOrder> findByItemId(@Param("tenant") String tenantId, @Param("itemId") Long orderItemId);
    }

    public interface RefundCaseRepository extends ListCrudRepository<RefundCase, Long> {
        List<RefundCase> findByTenantIdAndUserId(String tenantId, String userId);
    }
}
