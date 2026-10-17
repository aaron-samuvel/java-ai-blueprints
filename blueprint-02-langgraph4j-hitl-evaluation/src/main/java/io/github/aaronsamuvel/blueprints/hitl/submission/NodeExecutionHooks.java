package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.action.AsyncNodeActionWithConfig;
import org.bsc.langgraph4j.hook.NodeHook;

/**
 * A LangGraph4j wrap-call hook applied to every node:
 * <ul>
 *   <li>opens a Micrometer {@link Observation} named {@value #OBSERVATION} with the node id as a tag. With
 *       {@code micrometer-tracing-bridge-otel} on the classpath each observation becomes an OpenTelemetry span;</li>
 *   <li>runs the node on the given executor (virtual threads in the app) with a hard timeout, failing the
 *       graph run with {@link NodeTimeoutException} when a node, typically an LLM call, hangs.</li>
 * </ul>
 */
public final class NodeExecutionHooks implements NodeHook.WrapCall<ReviewWorkflowState> {

    public static final String OBSERVATION = "langgraph4j.node";

    /** A node that ran longer than its budget. */
    public static final class NodeTimeoutException extends RuntimeException {
        public NodeTimeoutException(String nodeId, Duration timeout) {
            super("node '" + nodeId + "' exceeded its timeout of " + timeout.toMillis() + " ms");
        }
    }

    private final ObservationRegistry registry;
    private final Duration timeout;
    private final Executor executor;

    public NodeExecutionHooks(ObservationRegistry registry, Duration timeout, Executor executor) {
        this.registry = registry;
        this.timeout = timeout;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Map<String, Object>> applyWrap(String nodeId, ReviewWorkflowState state, RunnableConfig config,
                                                            AsyncNodeActionWithConfig<ReviewWorkflowState> action) {
        Observation observation = Observation.createNotStarted(OBSERVATION, registry)
                .lowCardinalityKeyValue("graph", "submission-review")
                .lowCardinalityKeyValue("node", nodeId)
                .start();
        return CompletableFuture.supplyAsync(() -> {
                    try (Observation.Scope ignored = observation.openScope()) {
                        return action.apply(state, config).join();
                    }
                }, executor)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((result, error) -> {
                    if (error == null) {
                        observation.stop();
                        return result;
                    }
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    RuntimeException failure = cause instanceof TimeoutException
                            ? new NodeTimeoutException(nodeId, timeout)
                            : cause instanceof RuntimeException re ? re : new CompletionException(cause);
                    observation.error(failure);
                    observation.stop();
                    throw failure;
                });
    }
}
