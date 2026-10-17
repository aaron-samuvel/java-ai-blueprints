package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs branches concurrently on virtual threads against the same context and waits for all of
 * them, up to {@code timeout}. The first branch failure fails the step (the others are cancelled).
 */
public final class ParallelAgent implements AgentStep {

    private final String name;
    private final List<AgentStep> branches;
    private final Duration timeout;

    public ParallelAgent(String name, Duration timeout, AgentStep... branches) {
        this.name = name;
        this.branches = List.of(branches);
        this.timeout = timeout;
    }

    @Override
    public void run(WorkflowContext ctx) {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (AgentStep b : branches) futures.add(pool.submit(() -> b.run(ctx)));
            long deadline = System.nanoTime() + timeout.toNanos();
            for (Future<?> f : futures) {
                try {
                    f.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                } catch (ExecutionException e) {
                    futures.forEach(x -> x.cancel(true));
                    throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
                } catch (TimeoutException e) {
                    futures.forEach(x -> x.cancel(true));
                    throw new LoopGuardException(name + " timed out after " + timeout.toMillis() + " ms");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", e);
                }
            }
        }
    }

    @Override
    public String name() {
        return name;
    }
}
