package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

import java.util.function.Predicate;

/**
 * Repeats {@code body} until {@code done} holds, at most {@code maxIterations} times. Stores the
 * iteration count under {@code <name>.iterations}. When the cap is reached without success it
 * either stops quietly (the caller inspects the state) or throws, per {@code failOnCap}.
 */
public final class LoopAgent implements AgentStep {

    private final String name;
    private final AgentStep body;
    private final Predicate<WorkflowContext> done;
    private final int maxIterations;
    private final boolean failOnCap;
    private final ContextCompactor compactor;

    public LoopAgent(String name, AgentStep body, Predicate<WorkflowContext> done, int maxIterations, boolean failOnCap, ContextCompactor compactor) {
        if (maxIterations < 1) throw new IllegalArgumentException("maxIterations must be >= 1");
        this.name = name;
        this.body = body;
        this.done = done;
        this.maxIterations = maxIterations;
        this.failOnCap = failOnCap;
        this.compactor = compactor;
    }

    @Override
    public void run(WorkflowContext ctx) {
        for (int i = 1; i <= maxIterations; i++) {
            ctx.put(name + ".iterations", i);
            body.run(ctx);
            if (compactor != null) compactor.compactIfNeeded(ctx);
            if (done.test(ctx)) return;
        }
        if (failOnCap) throw new LoopGuardException(name + " did not finish within " + maxIterations + " iterations");
    }

    @Override
    public String name() {
        return name;
    }
}
