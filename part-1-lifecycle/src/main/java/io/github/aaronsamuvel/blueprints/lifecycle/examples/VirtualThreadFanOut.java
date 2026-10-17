package io.github.aaronsamuvel.blueprints.lifecycle.examples;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Pillar 1 (virtual threads): blocking LLM calls are I/O-bound, so fan them out on a
 * virtual-thread-per-task executor instead of a reactive pipeline. In a Boot app the same
 * effect is enabled with {@code spring.threads.virtual.enabled=true}.
 */
public final class VirtualThreadFanOut {

    private VirtualThreadFanOut() {}

    public static <T, R> List<R> mapConcurrently(List<T> inputs, Function<T, R> call) {
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<R>> futures = inputs.stream().map(i -> exec.submit(() -> call.apply(i))).toList();
            return futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException("call failed", e);
                }
            }).toList();
        }
    }
}
