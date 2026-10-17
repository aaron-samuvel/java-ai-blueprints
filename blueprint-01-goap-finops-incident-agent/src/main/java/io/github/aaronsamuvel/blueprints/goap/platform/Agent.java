package io.github.aaronsamuvel.blueprints.goap.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a bean whose {@link Action} methods the {@link AgentPlatform} plans over. Same idea as
 * Embabel's {@code @Agent}: the planner reads preconditions from parameter types and effects
 * from return types, so no LLM decides which step runs next.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Agent {
    String description();
}
