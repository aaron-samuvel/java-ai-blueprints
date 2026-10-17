package io.github.aaronsamuvel.blueprints.nl2sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import io.github.aaronsamuvel.blueprints.nl2sql.agent.Nl2SqlAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.agent.Nl2SqlResult;
import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlGuard;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class Nl2SqlAgentTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true)
                .addScripts("schema.sql", "data.sql").build();
        jdbc = new JdbcTemplate(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private static StubChatModel scripted(String... replies) {
        Deque<String> q = new ArrayDeque<>(List.of(replies));
        return new StubChatModel(p -> q.isEmpty() ? "SELECT 1" : q.poll());
    }

    private Nl2SqlAgent agent(StubChatModel model) {
        return new Nl2SqlAgent(model, new SqlGuard(500, Set.of("customers", "orders")), jdbc, "customers(id,name,city), orders(id,customer_id,amount_inr,placed_on)", 3);
    }

    @Test
    void firstTrySuccess() {
        Nl2SqlResult r = agent(scripted("```sql\nSELECT city, COUNT(*) AS n FROM customers GROUP BY city ORDER BY city;\n```"))
                .answer("customers per city");
        assertThat(r.success()).isTrue();
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.rows()).hasSize(3);
        assertThat(r.rows().get(0)).containsEntry("CITY", "Bengaluru");
    }

    @Test
    void selfHealsFromDatabaseErrorUsingFeedback() {
        StubChatModel model = scripted(
                "SELECT customer_name FROM customers",            // wrong column -> H2 error
                "SELECT name FROM customers WHERE city = 'Kochi'");
        Nl2SqlResult r = agent(model).answer("who lives in Kochi?");
        assertThat(r.success()).isTrue();
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.errors()).singleElement().asString().containsIgnoringCase("CUSTOMER_NAME");
        // the second prompt carried the failing SQL and the database error back to the model
        String retryPrompt = StubChatModel.lastText(model.received().get(1));
        assertThat(retryPrompt).contains("SELECT customer_name FROM customers").containsIgnoringCase("customer_name").contains("failed with");
        assertThat(r.rows()).singleElement().satisfies(row -> assertThat(row).containsEntry("NAME", "Meera Nair"));
    }

    @Test
    void selfHealsFromGuardRejection() {
        Nl2SqlResult r = agent(scripted("DELETE FROM orders", "SELECT COUNT(*) AS n FROM orders")).answer("how many orders?");
        assertThat(r.success()).isTrue();
        assertThat(r.attempts()).isEqualTo(2);
        assertThat(r.errors().get(0)).contains("only SELECT is allowed");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(606); // nothing deleted
    }

    @Test
    void givesUpAfterThreeRetries() {
        StubChatModel model = scripted("DROP TABLE orders", "SELECT nope FROM orders", "SELECT * FROM payroll", "UPDATE orders SET amount_inr = 0", "SELECT 1");
        Nl2SqlResult r = agent(model).answer("break things");
        assertThat(r.success()).isFalse();
        assertThat(r.attempts()).isEqualTo(4);
        assertThat(model.received()).hasSize(4); // 1 try + 3 retries, never a 5th call
        assertThat(r.errors()).hasSize(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(606);
    }

    @Test
    void limit500IsEnforcedOnRealRows() {
        Nl2SqlResult r = agent(scripted("SELECT * FROM orders")).answer("all orders");
        assertThat(r.sql()).endsWith("LIMIT 500");
        assertThat(r.rows()).hasSize(500);
    }
}
