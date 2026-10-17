package io.github.aaronsamuvel.blueprints.swarm.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantSessionKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:commerce;DB_CLOSE_DELAY=-1")
class CommerceSwarmApiTest {

    @Autowired
    Environment env;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AgentOrchestratorService swarm;

    private RestClient http() {
        return RestClient.create("http://localhost:" + env.getProperty("local.server.port"));
    }

    private JsonNode chat(String tenant, String user, String session, String message) {
        return http().post().uri("/api/v1/swarm/chat").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("tenantId", tenant, "userId", user, "sessionId", session, "message", message))
                .retrieve().body(JsonNode.class);
    }

    private static String session() {
        return "s-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void productDiscoveryUsesHybridSearchWithinTheTenant() {
        JsonNode r = chat("acme", "u-asha", session(), "I am looking for noise cancelling wireless headphones");
        assertThat(r.get("route").asString()).isEqualTo("PRODUCT_DISCOVERY");
        assertThat(r.get("handoffs").get(0).get("to").asString()).isEqualTo("product-discovery");
        assertThat(r.get("data").get("recommendations").get(0).get("sku").asString()).isEqualTo("SKU-HDP-01");
        assertThat(r.get("data").get("recommendations").get(0).get("name").asString()).isEqualTo("Studio wireless headphones");
        assertThat(r.toString()).doesNotContain("Globex");
    }

    @Test
    void orderAgentPlacesAnOrderThroughAToolCallInOneTransaction() {
        Integer before = jdbc.queryForObject("SELECT stock FROM products WHERE tenant_id='acme' AND sku='SKU-MSE-04'", Integer.class);
        JsonNode r = chat("acme", "u-asha", session(), "Please order 2 of SKU-MSE-04");
        assertThat(r.get("route").asString()).isEqualTo("ORDER_EXECUTION");
        assertThat(r.get("agent").asString()).isEqualTo("order-execution");
        assertThat(r.get("data").get("toolCalls").get(0).asString()).startsWith("placeOrder(SKU-MSE-04, 2) -> order ");
        assertThat(r.get("answer").asString()).contains("placeOrder").contains("\"totalInr\":\"3998.00\"");
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE tenant_id='acme' AND sku='SKU-MSE-04'", Integer.class)).isEqualTo(before - 2);
    }

    @Test
    void outOfStockOrderRollsBackAndNothingIsWritten() {
        Integer orders = jdbc.queryForObject("SELECT COUNT(*) FROM customer_orders", Integer.class);
        JsonNode r = chat("acme", "u-asha", session(), "Buy 9 of SKU-KBD-03");
        assertThat(r.get("answer").asString()).contains("only 3 units");
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE tenant_id='acme' AND sku='SKU-KBD-03'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_orders", Integer.class)).isEqualTo(orders);
    }

    @Autowired
    io.github.aaronsamuvel.blueprints.swarm.commerce.agents.OrderTools orderTools;

    @Test
    void aFailureAfterTheStockUpdateRollsTheStockBack() {
        assertThat(org.springframework.aop.support.AopUtils.isAopProxy(orderTools)).as("@Transactional proxy").isTrue();
        Integer before = jdbc.queryForObject("SELECT stock FROM products WHERE tenant_id='acme' AND sku='SKU-EAR-02'", Integer.class);
        // No customer bound: placeOrder decrements stock, then fails while building the order row.
        assertThatThrownBy(() -> io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext.callAs("acme",
                () -> orderTools.placeOrder("SKU-EAR-02", 1))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT stock FROM products WHERE tenant_id='acme' AND sku='SKU-EAR-02'", Integer.class)).isEqualTo(before);
    }

    @Test
    void orderStatusIsLimitedToTheCustomersOwnOrders() {
        assertThat(chat("acme", "u-asha", session(), "What is the status of order 1001?").get("answer").asString())
                .contains("DELIVERED").contains("item 5001");
        assertThat(chat("acme", "u-other", session(), "What is the status of order 1001?").get("answer").asString())
                .contains("not found for this customer");
    }

    @Test
    void refundIsCorrectedByTheEvaluatorOptimizerLoopAndPersisted() {
        JsonNode r = chat("acme", "u-asha", session(), "I want a refund for item 5002, 1 unit arrived damaged");
        assertThat(r.get("route").asString()).isEqualTo("REFUND_DISPUTE");
        assertThat(r.get("data").get("decision").asString()).isEqualTo("APPROVE");
        JsonNode rounds = r.get("data").get("rounds");
        assertThat(rounds.size()).isEqualTo(2);
        assertThat(rounds.get(0).get("violations").get(0).asString()).contains("at most INR 2499.00");
        assertThat(rounds.get(1).get("violations").size()).isZero();
        assertThat(r.get("answer").asString()).contains("INR 2499.00");
        assertThat(jdbc.queryForObject("SELECT refunded_quantity FROM order_items WHERE id=5002", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_inr FROM refund_cases WHERE order_item_id=5002", BigDecimal.class))
                .isEqualByComparingTo("2499.00");
    }

    @Test
    void refundOutsideTheWindowIsDeniedAndRefundNeedsAnItemTheUserOwns() {
        JsonNode old = chat("acme", "u-asha", session(), "Refund item 5003 please, the mouse broke");
        assertThat(old.get("data").get("decision").asString()).isEqualTo("DENY");
        assertThat(old.get("answer").asString()).contains("can't be approved");

        assertThat(chat("acme", "u-asha", session(), "I want my money back").get("data").get("needs").asString()).isEqualTo("orderItemId");
        assertThat(chat("acme", "u-asha", session(), "Refund item 6001").get("data").get("found").asBoolean())
                .as("item 6001 belongs to tenant globex").isFalse();
    }

    @Test
    void historyIsPartitionedByTenantUserAndSession() {
        String s = session();
        chat("acme", "u-asha", s, "hello there");
        chat("acme", "u-asha", s, "I am looking for running earbuds");
        JsonNode history = http().get().uri("/api/v1/swarm/sessions/{s}/history", s)
                .header("X-Tenant-Id", "acme").header("X-User-Id", "u-asha").retrieve().body(JsonNode.class);
        assertThat(history.size()).isEqualTo(4);
        assertThat(history.get(0).get("content").asString()).isEqualTo("hello there");
        JsonNode otherUser = http().get().uri("/api/v1/swarm/sessions/{s}/history", s)
                .header("X-Tenant-Id", "acme").header("X-User-Id", "u-ben").retrieve().body(JsonNode.class);
        assertThat(otherUser.size()).isZero();
        JsonNode otherTenant = http().get().uri("/api/v1/swarm/sessions/{s}/history", s)
                .header("X-Tenant-Id", "globex").header("X-User-Id", "u-asha").retrieve().body(JsonNode.class);
        assertThat(otherTenant.size()).isZero();
    }

    @Test
    void streamingEndpointSendsHandoffThenReply() {
        String body = http().post().uri("/api/v1/swarm/chat/stream").contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .body(Map.of("tenantId", "acme", "userId", "u-asha", "sessionId", session(), "message", "recommend an ergonomic mouse"))
                .retrieve().body(String.class);
        assertThat(body).contains("event:handoff").contains("\"to\":\"product-discovery\"").contains("event:reply");
        assertThat(body.indexOf("event:handoff")).isLessThan(body.indexOf("event:reply"));
    }

    @Test
    void handoffBadgePageIsServed() {
        String page = http().get().uri("/swarm.html").retrieve().body(String.class);
        assertThat(page).contains("api/v1/swarm/chat/stream").contains("handoff");
    }

    @Test
    void memoryRefusesAConversationOfAnotherTenant() {
        TenantSessionKey globexKey = new TenantSessionKey("globex", "u-ben", "s-x");
        assertThatThrownBy(() -> io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext.callAs("acme",
                () -> swarm.history(globexKey).size() + new io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantPartitionedChatMemory(
                        jdbc, 12, 6, null).get(globexKey.conversationId()).size()))
                .isInstanceOf(SecurityException.class);
    }
}
