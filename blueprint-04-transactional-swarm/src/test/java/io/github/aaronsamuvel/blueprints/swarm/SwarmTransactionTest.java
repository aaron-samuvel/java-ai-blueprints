package io.github.aaronsamuvel.blueprints.swarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import io.github.aaronsamuvel.blueprints.swarm.agents.RefundLimitExceededException;
import io.github.aaronsamuvel.blueprints.swarm.agents.SwarmAgents;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantJdbcChatMemoryRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Real Spring context, real @Transactional proxy, real H2. The scripted model plays all three
 * agents (chosen by system prompt); each test sets what the refund and notify agents answer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SwarmTransactionTest {

    static final class Script {
        volatile String refundJson = "{\"orderId\":\"ORD-1\",\"amountInr\":1499.00,\"reason\":\"damaged\"}";
        volatile String notifyText = "Your refund of INR 1499.00 for ORD-1 is on its way.";
        volatile String triage = "REFUND";
    }

    static final Script SCRIPT = new Script();

    @TestConfiguration
    static class Models {
        @Bean
        @Primary
        StubChatModel scriptedSwarmModel() {
            return new StubChatModel((Prompt p) -> {
                String all = p.getContents();
                if (all.contains(SwarmAgents.TRIAGE_PROMPT)) return SCRIPT.triage;
                if (all.contains(SwarmAgents.REFUND_PROMPT)) return SCRIPT.refundJson;
                return SCRIPT.notifyText;
            });
        }
    }

    @Autowired SwarmCoordinator coordinator;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantJdbcChatMemoryRepository memory;
    @Autowired StubChatModel model;
    @Autowired Environment env;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM chat_memory");
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM outbox");
        SCRIPT.refundJson = "{\"orderId\":\"ORD-1\",\"amountInr\":1499.00,\"reason\":\"damaged\"}";
        SCRIPT.notifyText = "Your refund of INR 1499.00 for ORD-1 is on its way.";
        SCRIPT.triage = "REFUND";
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void happyPathCommitsRefundOutboxAndMemoryTogether() {
        SwarmResult r = TenantContext.callAs("acme", () -> coordinator.handle("c-1", "My order ORD-1 arrived broken, refund please"));
        assertThat(r.route()).isEqualTo("REFUND");
        assertThat(r.refund().orderId()).isEqualTo("ORD-1");
        assertThat(count("refunds")).isEqualTo(1);
        assertThat(count("outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_memory WHERE tenant_id='acme' AND conversation_id='c-1'", Integer.class))
                .isEqualTo(6); // 3 agents x (user + assistant)
    }

    @Test
    void notifierFailureRollsBackRefundAndMemory() {
        SCRIPT.notifyText = "   ";
        assertThatThrownBy(() -> TenantContext.callAs("acme", () -> coordinator.handle("c-1", "refund ORD-1")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("empty message");
        assertThat(count("refunds")).isZero();   // refund row was inserted, then rolled back
        assertThat(count("outbox")).isZero();
        assertThat(count("chat_memory")).isZero(); // triage + refund memory rolled back too
    }

    @Test
    void businessRuleViolationRollsBackEverything() {
        SCRIPT.refundJson = "{\"orderId\":\"ORD-9\",\"amountInr\":99999.00,\"reason\":\"too much\"}";
        assertThatThrownBy(() -> TenantContext.callAs("acme", () -> coordinator.handle("c-2", "refund ORD-9")))
                .isInstanceOf(RefundLimitExceededException.class);
        assertThat(count("refunds")).isZero();
        assertThat(count("chat_memory")).isZero();
    }

    @Test
    void nonRefundRouteWritesOnlyMemory() {
        SCRIPT.triage = "OTHER";
        SwarmResult r = TenantContext.callAs("acme", () -> coordinator.handle("c-3", "What are your opening hours?"));
        assertThat(r.refund()).isNull();
        assertThat(count("refunds")).isZero();
        assertThat(count("chat_memory")).isEqualTo(2);
    }

    @Test
    void memoryIsPartitionedByTenantEvenWithSameConversationId() {
        TenantContext.callAs("acme", () -> coordinator.handle("shared-id", "acme secret order ORD-1"));
        SCRIPT.triage = "OTHER";
        TenantContext.callAs("globex", () -> coordinator.handle("shared-id", "hello from globex"));

        String acmeHistory = TenantContext.callAs("acme", () -> memory.findByConversationId("shared-id").toString());
        String globexHistory = TenantContext.callAs("globex", () -> memory.findByConversationId("shared-id").toString());
        assertThat(acmeHistory).contains("acme secret").doesNotContain("globex");
        assertThat(globexHistory).contains("hello from globex").doesNotContain("acme secret");
        assertThat(TenantContext.callAs("globex", memory::findConversationIds)).containsExactly("shared-id");
        // the model, acting for globex, never saw acme's messages
        assertThat(StubChatModel.lastText(model.lastPrompt())).doesNotContain("acme");
        assertThat(model.lastPrompt().getContents()).doesNotContain("acme secret");
    }

    @Test
    void memoryCarriesAcrossTurnsWithinATenant() {
        SCRIPT.triage = "OTHER";
        TenantContext.callAs("acme", () -> coordinator.handle("c-4", "My name is Asha"));
        TenantContext.callAs("acme", () -> coordinator.handle("c-4", "What is my name?"));
        assertThat(model.lastPrompt().getContents()).contains("My name is Asha");
    }

    @Test
    void noTenantFailsClosed() {
        assertThatThrownBy(() -> coordinator.handle("c-5", "refund")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tenant");
        assertThatThrownBy(() -> TenantContext.callAs("ACME; DROP", () -> 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void httpEndpointUsesTenantHeader() {
        Map<String, Object> body = RestClient.create("http://localhost:" + env.getProperty("local.server.port"))
                .post().uri("/api/conversations/{id}/messages", "web-1")
                .header("X-Tenant-Id", "acme").header("Content-Type", "application/json")
                .body(Map.of("message", "refund ORD-1 please")).retrieve().body(Map.class);
        assertThat(body.get("route")).isEqualTo("REFUND");
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM refunds", String.class)).isEqualTo("acme");
    }
}
