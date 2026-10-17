package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.sql.DataSource;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.InterruptionMetadata;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.bsc.langgraph4j.serializer.Serializer;
import org.bsc.langgraph4j.serializer.std.StdCheckpointListSerializer;
import org.bsc.langgraph4j.serializer.std.StdStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A durable LangGraph4j checkpoint saver over any JDBC database (H2 in tests, PostgreSQL in production).
 * Each thread's checkpoint list is stored as one row, serialized with LangGraph4j's own
 * {@link StdCheckpointListSerializer}, so a paused human-review thread survives an application restart.
 * Released threads are kept (flagged {@code released}) for audit rather than deleted.
 */
public class JdbcCheckpointSaver extends AbstractCheckpointSaver {

    public static final String TABLE = "lg4j_checkpoints";

    private final JdbcTemplate jdbc;
    private final Serializer<LinkedList<Checkpoint>> serializer;

    public JdbcCheckpointSaver(DataSource dataSource, StdStateSerializer<? extends AgentState> stateSerializer) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.serializer = new StdCheckpointListSerializer(stateSerializer);
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " (thread_id VARCHAR(128) PRIMARY KEY, "
                + "checkpoints TEXT NOT NULL, released BOOLEAN DEFAULT FALSE NOT NULL, updated_at TIMESTAMP NOT NULL)");
    }

    /** The released checkpoints of a thread, if it was released (versions are not kept separately). */
    @Override
    public Optional<BaseCheckpointSaver.Tag> tag(RunnableConfig config, Integer version) throws Exception {
        List<String> rows = jdbc.queryForList("SELECT checkpoints FROM " + TABLE + " WHERE thread_id = ? AND released = TRUE",
                String.class, threadId(config));
        if (rows.isEmpty()) return Optional.empty();
        return Optional.of(new BaseCheckpointSaver.Tag(threadId(config), serializer.readDataFromString(rows.getFirst())));
    }

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        List<String> rows = jdbc.queryForList("SELECT checkpoints FROM " + TABLE + " WHERE thread_id = ? AND released = FALSE",
                String.class, threadId(config));
        return rows.isEmpty() ? new LinkedList<>() : serializer.readDataFromString(rows.getFirst());
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        store(config, checkpoints);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint) throws Exception {
        store(config, checkpoints);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, String reason) {
        jdbc.update("UPDATE " + TABLE + " SET released = TRUE, updated_at = ? WHERE thread_id = ?", Timestamp.from(Instant.now()), threadId(config));
        return new BaseCheckpointSaver.Tag(threadId(config), checkpoints);
    }

    @Override
    protected BaseCheckpointSaver.Tag releaseCheckpointsOnError(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Throwable error) {
        return releaseCheckpoints(config, checkpoints, error.getMessage());
    }

    /** Interruptions need no extra bookkeeping: the paused thread's checkpoint already says what runs next. */
    @Override
    public <State extends AgentState> CompletableFuture<InterruptionMetadata<State>> registerInterruption(
            RunnableConfig config, InterruptionMetadata<State> metadata) {
        return CompletableFuture.completedFuture(metadata);
    }

    /** Number of rows, for tests and the smoke script. */
    public int storedThreads() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class);
        return n == null ? 0 : n;
    }

    private void store(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String data = serializer.writeDataAsString(checkpoints);
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update("UPDATE " + TABLE + " SET checkpoints = ?, released = FALSE, updated_at = ? WHERE thread_id = ?",
                data, now, threadId(config));
        if (updated == 0) {
            jdbc.update("INSERT INTO " + TABLE + " (thread_id, checkpoints, released, updated_at) VALUES (?, ?, FALSE, ?)",
                    threadId(config), data, now);
        }
    }
}
