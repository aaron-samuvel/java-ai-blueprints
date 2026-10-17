package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

/** One unit of work in a workflow. Compose with {@link SequentialAgent}, {@link ParallelAgent} and {@link LoopAgent}. */
@FunctionalInterface
public interface AgentStep {

    void run(WorkflowContext ctx);

    default String name() {
        return getClass().getSimpleName();
    }

    static AgentStep named(String name, AgentStep step) {
        return new AgentStep() {
            @Override
            public void run(WorkflowContext ctx) {
                step.run(ctx);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }
}
