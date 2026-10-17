package io.github.aaronsamuvel.blueprints.nl2sql.workflow;

import java.util.List;

/** Runs steps in order, compacting the transcript after each one. */
public final class SequentialAgent implements AgentStep {

    private final String name;
    private final List<AgentStep> steps;
    private final ContextCompactor compactor;

    public SequentialAgent(String name, ContextCompactor compactor, AgentStep... steps) {
        this.name = name;
        this.steps = List.of(steps);
        this.compactor = compactor;
    }

    @Override
    public void run(WorkflowContext ctx) {
        for (AgentStep s : steps) {
            s.run(ctx);
            if (compactor != null) compactor.compactIfNeeded(ctx);
        }
    }

    @Override
    public String name() {
        return name;
    }
}
