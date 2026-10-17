package io.github.aaronsamuvel.blueprints.swarm.memory;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@link ChatMemoryRepository} partitioned by tenant: every query has {@code WHERE tenant_id = ?},
 * with the tenant taken from {@link TenantContext}, never from the caller. Two tenants using the
 * same conversation id get separate histories. It uses the same DataSource as business tables,
 * so memory writes join the surrounding {@code @Transactional} and roll back with it.
 */
public class TenantJdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbc;

    public TenantJdbcChatMemoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbc.queryForList("SELECT DISTINCT conversation_id FROM chat_memory WHERE tenant_id = ? ORDER BY conversation_id",
                String.class, TenantContext.require());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbc.query("SELECT role, content FROM chat_memory WHERE tenant_id = ? AND conversation_id = ? ORDER BY seq",
                (rs, i) -> toMessage(rs.getString("role"), rs.getString("content")),
                TenantContext.require(), conversationId);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        String tenant = TenantContext.require();
        jdbc.update("DELETE FROM chat_memory WHERE tenant_id = ? AND conversation_id = ?", tenant, conversationId);
        int seq = 0;
        for (Message m : messages) {
            jdbc.update("INSERT INTO chat_memory (tenant_id, conversation_id, seq, role, content) VALUES (?,?,?,?,?)",
                    tenant, conversationId, seq++, m.getMessageType().name(), m.getText());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbc.update("DELETE FROM chat_memory WHERE tenant_id = ? AND conversation_id = ?", TenantContext.require(), conversationId);
    }

    private static Message toMessage(String role, String content) {
        return switch (MessageType.valueOf(role)) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new SystemMessage(content);
            default -> throw new IllegalStateException("unsupported role " + role);
        };
    }
}
