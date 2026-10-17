package io.github.aaronsamuvel.blueprints.swarm.commerce.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A {@link ChatMemory} partitioned by {@link TenantSessionKey} and stored in one JDBC table.
 * <ul>
 *   <li><b>Isolation</b>: every read and write is keyed by tenant, user and session. The conversation's
 *       tenant must equal the caller's tenant ({@link TenantContext}), otherwise the call fails closed.</li>
 *   <li><b>Sliding-window compaction</b>: when a session holds more than {@code maxMessages}, everything
 *       except the newest {@code keepRecent} messages is replaced by one summary message, so prompts stay
 *       bounded without silently dropping what the customer said earlier.</li>
 * </ul>
 */
public class TenantPartitionedChatMemory implements ChatMemory {

    /** Turns the messages being compacted into one summary text. */
    public interface Summarizer extends Function<List<Message>, String> {}

    public static final String SUMMARY_PREFIX = "Summary of earlier conversation: ";

    private final JdbcTemplate jdbc;
    private final int maxMessages;
    private final int keepRecent;
    private final Summarizer summarizer;
    private final ReentrantLock lock = new ReentrantLock();

    public TenantPartitionedChatMemory(JdbcTemplate jdbc, int maxMessages, int keepRecent, Summarizer summarizer) {
        if (keepRecent < 1 || keepRecent >= maxMessages) throw new IllegalArgumentException("need 1 <= keepRecent < maxMessages");
        this.jdbc = jdbc;
        this.maxMessages = maxMessages;
        this.keepRecent = keepRecent;
        this.summarizer = summarizer == null ? TenantPartitionedChatMemory::extractiveSummary : summarizer;
    }

    private static TenantSessionKey authorised(String conversationId) {
        TenantSessionKey key = TenantSessionKey.parse(conversationId);
        String caller = TenantContext.require();
        if (!caller.equals(key.tenantId())) {
            throw new SecurityException("tenant " + caller + " may not access a conversation of tenant " + key.tenantId());
        }
        return key;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        TenantSessionKey key = authorised(conversationId);
        lock.lock();
        try {
            Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(seq), 0) FROM swarm_chat_memory WHERE tenant_id=? AND user_id=? AND session_id=?",
                    Integer.class, key.tenantId(), key.userId(), key.sessionId());
            int seq = max == null ? 0 : max;
            for (Message m : messages) {
                if (m.getMessageType() == MessageType.TOOL) continue; // tool traffic stays out of long-term memory
                jdbc.update("INSERT INTO swarm_chat_memory (tenant_id, user_id, session_id, seq, role, content) VALUES (?,?,?,?,?,?)",
                        key.tenantId(), key.userId(), key.sessionId(), ++seq, m.getMessageType().name(), m.getText() == null ? "" : m.getText());
            }
            compact(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        return load(authorised(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        TenantSessionKey key = authorised(conversationId);
        jdbc.update("DELETE FROM swarm_chat_memory WHERE tenant_id=? AND user_id=? AND session_id=?", key.tenantId(), key.userId(), key.sessionId());
    }

    private List<Message> load(TenantSessionKey key) {
        return jdbc.query("SELECT role, content FROM swarm_chat_memory WHERE tenant_id=? AND user_id=? AND session_id=? ORDER BY seq",
                (rs, i) -> toMessage(rs.getString(1), rs.getString(2)), key.tenantId(), key.userId(), key.sessionId());
    }

    private void compact(TenantSessionKey key) {
        List<Integer> seqs = jdbc.queryForList("SELECT seq FROM swarm_chat_memory WHERE tenant_id=? AND user_id=? AND session_id=? ORDER BY seq",
                Integer.class, key.tenantId(), key.userId(), key.sessionId());
        if (seqs.size() <= maxMessages) return;
        List<Message> all = load(key);
        int cut = seqs.size() - keepRecent;
        String summary = SUMMARY_PREFIX + summarizer.apply(all.subList(0, cut));
        int lastCompacted = seqs.get(cut - 1);
        jdbc.update("DELETE FROM swarm_chat_memory WHERE tenant_id=? AND user_id=? AND session_id=? AND seq <= ?",
                key.tenantId(), key.userId(), key.sessionId(), lastCompacted);
        jdbc.update("INSERT INTO swarm_chat_memory (tenant_id, user_id, session_id, seq, role, content) VALUES (?,?,?,?,?,?)",
                key.tenantId(), key.userId(), key.sessionId(), lastCompacted, MessageType.SYSTEM.name(), summary);
    }

    private static Message toMessage(String role, String content) {
        return switch (MessageType.valueOf(role)) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            default -> new SystemMessage(content);
        };
    }

    /** Default summarizer (no LLM): keeps a short trace of what the user asked and what was answered. */
    static String extractiveSummary(List<Message> messages) {
        List<String> lines = new ArrayList<>();
        for (Message m : messages) {
            String text = m.getText() == null ? "" : m.getText().strip();
            if (text.startsWith(SUMMARY_PREFIX)) text = text.substring(SUMMARY_PREFIX.length());
            String clipped = text.length() > 90 ? text.substring(0, 90) + "..." : text;
            lines.add((m.getMessageType() == MessageType.USER ? "user: " : m.getMessageType() == MessageType.ASSISTANT ? "agent: " : "") + clipped);
        }
        return lines.stream().collect(Collectors.joining(" | "));
    }
}
