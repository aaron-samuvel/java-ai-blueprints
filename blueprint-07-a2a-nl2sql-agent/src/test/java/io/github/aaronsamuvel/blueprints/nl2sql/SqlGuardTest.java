package io.github.aaronsamuvel.blueprints.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlGuard;
import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlRejectedException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlGuardTest {

    private final SqlGuard guard = new SqlGuard(500, Set.of("customers", "orders"));

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM orders",
            "UPDATE customers SET name = 'x'",
            "DROP TABLE orders",
            "INSERT INTO customers VALUES (9,'a','b')",
            "TRUNCATE TABLE orders",
            "CREATE TABLE t (id INT)",
            "SELECT * FROM customers; DELETE FROM orders",
            "SELECT * FROM information_schema.users",
            "SELECT * FROM orders FOR UPDATE",
            "SELEC name FROM customers",
            ""})
    void rejectsAnythingButOneSafeSelect(String sql) {
        assertThatThrownBy(() -> guard.enforce(sql)).isInstanceOf(SqlRejectedException.class);
    }

    @Test
    void addsLimitWhenMissing() {
        assertThat(guard.enforce("SELECT name FROM customers")).isEqualTo("SELECT name FROM customers LIMIT 500");
    }

    @Test
    void capsLargeLimitAndKeepsSmallOne() {
        assertThat(guard.enforce("SELECT * FROM orders LIMIT 100000")).endsWith("LIMIT 500");
        assertThat(guard.enforce("SELECT * FROM orders LIMIT 10")).endsWith("LIMIT 10");
    }

    @Test
    void allowsJoinsAggregatesAndUnionOnAllowedTables() {
        assertThat(guard.enforce("SELECT c.city, COUNT(*) FROM customers c JOIN orders o ON o.customer_id = c.id GROUP BY c.city"))
                .endsWith("LIMIT 500");
        assertThat(guard.enforce("SELECT id FROM customers UNION SELECT id FROM orders")).endsWith("LIMIT 500");
    }

    @Test
    void rejectionMessageIsUsefulForSelfHealing() {
        assertThatThrownBy(() -> guard.enforce("SELECT * FROM payroll"))
                .hasMessageContaining("table not allowed: PAYROLL");
    }
}
