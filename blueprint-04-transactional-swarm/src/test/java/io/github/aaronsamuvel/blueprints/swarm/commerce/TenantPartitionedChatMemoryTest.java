package io.github.aaronsamuvel.blueprints.swarm.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantPartitionedChatMemory;
import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantSessionKey;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class TenantPartitionedChatMemoryTest {

    private JdbcTemplate jdbc;
    private TenantPartitionedChatMemory memory;
    private final TenantSessionKey key = new TenantSessionKey("acme", "u-1", "s-1");

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:mem" + System.nanoTime() + ";DB_CLOSE_DELAY=-1"));
        jdbc.execute("CREATE TABLE swarm_chat_memory (tenant_id VARCHAR(64), user_id VARCHAR(64), session_id VARCHAR(64), seq INT, role VARCHAR(20), content CLOB, PRIMARY KEY (tenant_id, user_id, session_id, seq))");
        memory = new TenantPartitionedChatMemory(jdbc, 6, 2, null);
    }

    @Test
    void compactsOldMessagesIntoOneSummaryAndKeepsTheRecentWindow() {
        TenantContext.callAs("acme", () -> {
            for (int i = 1; i <= 4; i++) {
                memory.add(key.conversationId(), List.of(new UserMessage("question " + i), new AssistantMessage("answer " + i)));
            }
            var msgs = memory.get(key.conversationId());
            assertThat(msgs).hasSizeLessThanOrEqualTo(6);
            assertThat(msgs.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
            assertThat(msgs.getFirst().getText()).startsWith(TenantPartitionedChatMemory.SUMMARY_PREFIX).contains("user: question 1");
            assertThat(msgs.getLast().getText()).isEqualTo("answer 4");
            assertThat(msgs.get(msgs.size() - 2).getText()).isEqualTo("question 4");
            return null;
        });
    }

    @Test
    void failsClosedWithoutOrWithTheWrongTenant() {
        assertThatThrownBy(() -> memory.get(key.conversationId())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TenantContext.callAs("globex", () -> memory.get(key.conversationId())))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> TenantSessionKey.parse("acme::u-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantSessionKey("acme", "u 1; drop", "s")).isInstanceOf(IllegalArgumentException.class);
    }
}
