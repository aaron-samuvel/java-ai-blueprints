package io.github.aaronsamuvel.blueprints.goap.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** On an {@link Action}: producing this action's return type is the agent's goal. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AchievesGoal {
    String description();
}
