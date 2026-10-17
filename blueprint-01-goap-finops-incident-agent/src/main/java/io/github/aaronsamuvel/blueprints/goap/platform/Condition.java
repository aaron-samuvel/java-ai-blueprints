package io.github.aaronsamuvel.blueprints.goap.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A boolean method over blackboard objects, re-evaluated after every action (the Observe step of
 * the OODA loop). Its name defaults to the method name. Until its inputs exist it is unknown and
 * the planner optimistically assumes it holds; the real value then drives replanning.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Condition {
    String name() default "";
}
