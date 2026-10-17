package io.github.aaronsamuvel.blueprints.goap.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A planning step. Preconditions: every parameter type must be on the blackboard (except {@link Ai}),
 * plus the {@link #pre()} conditions. Effect: the return value is put on the blackboard, plus the
 * {@link #post()} conditions are expected (for planning only; they are re-evaluated after the action).
 * Condition names may be negated with a leading {@code !}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {
    String description() default "";

    String[] pre() default {};

    String[] post() default {};

    /** Planner cost; cheaper plans win. */
    double cost() default 1;

    /** false: the action runs at most once per process (stops endless replanning loops). */
    boolean canRerun() default true;

    /** Tool groups the action's LLM calls may use; every other tool stays hidden from the model. */
    String[] toolGroups() default {};
}
