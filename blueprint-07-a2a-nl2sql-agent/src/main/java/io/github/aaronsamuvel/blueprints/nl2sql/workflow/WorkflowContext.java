package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared state of one workflow run: typed slots, a transcript of intermediate turns (what a model
 * would see as history), and a tool-call counter for the loop guard. Thread-safe, so
 * {@link ParallelAgent} branches can write to it.
 */
public final class WorkflowContext {

    private final Map<String, Object> state = new LinkedHashMap<>();
    private final List<String> transcript = new ArrayList<>();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private final int maxToolCalls;
    private int compactions;

    public WorkflowContext(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public synchronized WorkflowContext put(String key, Object value) {
        state.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public synchronized <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) state.get(key));
    }

    public synchronized <T> T require(String key) {
        return this.<T>get(key).orElseThrow(() -> new IllegalStateException("missing workflow state " + key));
    }

    public synchronized void remove(String key) {
        state.remove(key);
    }

    public synchronized void log(String entry) {
        transcript.add(entry);
    }

    public synchronized List<String> transcript() {
        return List.copyOf(transcript);
    }

    synchronized void replaceTranscript(List<String> entries) {
        transcript.clear();
        transcript.addAll(entries);
        compactions++;
    }

    public synchronized int compactions() {
        return compactions;
    }

    /** Counts one tool call (SQL execution, remote agent call...); throws when the guard trips. */
    public int countToolCall(String what) {
        int n = toolCalls.incrementAndGet();
        if (n > maxToolCalls) throw new LoopGuardException("tool-call budget of " + maxToolCalls + " exceeded at: " + what);
        return n;
    }

    public int toolCalls() {
        return toolCalls.get();
    }
}
