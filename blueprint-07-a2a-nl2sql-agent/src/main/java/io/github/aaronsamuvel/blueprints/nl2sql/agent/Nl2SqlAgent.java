package io.github.aaronsamuvel.blueprints.nl2sql.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlGuard;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Self-healing NL2SQL: the model proposes SQL, the guard validates and caps it, H2 (or any JDBC
 * database) runs it. When the guard or the database rejects the SQL, the error is sent back to
 * the model, up to {@code maxRetries} times (the handbook says 3), so at most 1 + 3 model calls.
 */
public class Nl2SqlAgent {

    static final String SYSTEM = """
            You translate questions into ONE read-only SQL SELECT for an H2 database.
            Reply with SQL only, no explanation, no markdown.
            Schema:
            %s""";

    private final ChatClient chat;
    private final SqlGuard guard;
    private final JdbcTemplate jdbc;
    private final String schema;
    private final int maxRetries;

    public Nl2SqlAgent(ChatModel model, SqlGuard guard, JdbcTemplate jdbc, String schema, int maxRetries) {
        this.chat = ChatClient.create(model);
        this.guard = guard;
        this.jdbc = jdbc;
        this.schema = schema;
        this.maxRetries = maxRetries;
    }

    public Nl2SqlResult answer(String question) {
        List<String> errors = new ArrayList<>();
        String lastSql = null;
        for (int attempt = 1; attempt <= 1 + maxRetries; attempt++) {
            String user = errors.isEmpty() ? question : """
                    %s

                    Your previous SQL:
                    %s
                    failed with: %s
                    Fix it and reply with corrected SQL only.""".formatted(question, lastSql, errors.get(errors.size() - 1));
            lastSql = clean(chat.prompt().system(SYSTEM.formatted(schema)).user(user).call().content());
            try {
                String safe = guard.enforce(lastSql);
                List<Map<String, Object>> rows = jdbc.queryForList(safe);
                return new Nl2SqlResult(true, safe, rows, attempt, errors);
            } catch (RuntimeException e) {
                errors.add(e.getClass().getSimpleName() + ": " + rootMessage(e));
            }
        }
        return new Nl2SqlResult(false, lastSql, List.of(), 1 + maxRetries, errors);
    }

    /** Strip markdown fences and trailing semicolons that small models like to add. */
    static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.strip().replaceAll("(?s)^```(?:sql)?\\s*", "").replaceAll("(?s)\\s*```$", "").strip();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).strip();
        return s;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = String.valueOf(t.getMessage());
        return m.lines().findFirst().orElse(m);
    }
}
