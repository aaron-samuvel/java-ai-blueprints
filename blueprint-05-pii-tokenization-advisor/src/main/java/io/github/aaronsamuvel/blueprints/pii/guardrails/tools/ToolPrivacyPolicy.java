package io.github.aaronsamuvel.blueprints.pii.guardrails.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;

/**
 * Least-privilege disclosure policy for a {@code @Tool} method (handbook Part III, Blueprint 5).
 * <ul>
 *   <li>{@link #requiredRole()}: Spring Security authority the caller needs. Without it, the tool
 *       is not even put into the model's tool schema.</li>
 *   <li>{@link #allowedPiiTypes()}: placeholder types restored to raw values in this tool's
 *       arguments. Other placeholders reach the tool unchanged.</li>
 * </ul>
 * The tool's result is always re-tokenised before it re-enters the model's context. A tool
 * without this annotation gets no restored values and needs no role.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolPrivacyPolicy {

    PiiEntityType[] allowedPiiTypes() default {};

    String requiredRole() default "";
}
