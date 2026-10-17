package io.github.aaronsamuvel.blueprints.nl2sql;

import java.util.Set;

import io.github.aaronsamuvel.blueprints.nl2sql.dataagent.SqlAstGuardrailValidator;
import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlRejectedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlAstGuardrailValidatorTest {

    private final SqlAstGuardrailValidator guard = new SqlAstGuardrailValidator(Set.of("SHOP_ORDERS", "SHOP_CUSTOMERS"), 500);

    @Test
    void injectsTenantPredicateForEveryTableAndCapsLimit() {
        var v = guard.validate("SELECT c.name, COUNT(*) FROM shop_customers c JOIN shop_orders o ON o.customer_id = c.id "
                + "WHERE c.city = 'Pune' OR c.city = 'Chennai' GROUP BY c.name LIMIT 9000", "acme");
        assertThat(v.sql()).contains("(c.city = 'Pune' OR c.city = 'Chennai') AND c.tenant_id = :tenantId AND o.tenant_id = :tenantId")
                .endsWith("LIMIT 500");
        assertThat(v.params()).containsEntry("tenantId", "acme");
        assertThat(guard.validate("SELECT id FROM shop_orders", "acme").sql())
                .isEqualTo("SELECT id FROM shop_orders WHERE shop_orders.tenant_id = :tenantId LIMIT 500");
        assertThat(guard.validate("SELECT id FROM shop_orders o LIMIT 10", "acme").sql()).endsWith("LIMIT 10");
    }

    @Test
    void rejectsWritesDdlSetOperationsSubqueriesCtesAndTenantReferences() {
        String[] bad = {
                "DROP TABLE shop_orders",
                "DELETE FROM shop_orders",
                "UPDATE shop_orders SET status = 'X'",
                "SELECT id FROM shop_orders; DROP TABLE shop_orders",
                "SELECT id FROM shop_orders UNION SELECT id FROM shop_customers",
                "SELECT id FROM shop_orders WHERE customer_id IN (SELECT id FROM shop_customers)",
                "SELECT x.id FROM (SELECT id FROM shop_orders) x",
                "WITH t AS (SELECT id FROM shop_orders) SELECT id FROM t",
                "SELECT id FROM shop_orders WHERE tenant_id = 'globex'",
                "SELECT id FROM shop_orders o WHERE 1=1 OR o.TENANT_ID <> 'x'",
                "SELECT * FROM shop_warehouses",
                "SELECT id INTO copy FROM shop_orders",
                "SELECT id FROM shop_orders FOR UPDATE"};
        for (String sql : bad) {
            assertThatThrownBy(() -> guard.validate(sql, "acme")).as(sql).isInstanceOf(SqlRejectedException.class);
        }
        assertThatThrownBy(() -> guard.validate("SELECT id FROM shop_orders", "ACME'; --")).isInstanceOf(SqlRejectedException.class);
    }
}
