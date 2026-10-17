package io.github.aaronsamuvel.blueprints.pii.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares what may happen to PII in a tool's output before it is returned to the model.
 * Tools without this annotation are treated as {@link Mode#TOKENIZE} (secure by default).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolPrivacyPolicy {

    Mode value() default Mode.TOKENIZE;

    /** Free-text justification, required for ALLOW_RAW in code review. */
    String reason() default "";

    enum Mode {
        /** Replace PII with reversible vault tokens. */
        TOKENIZE,
        /** Irreversibly mask PII. */
        REDACT,
        /** Pass output unchanged; use only for data that is not personal. */
        ALLOW_RAW
    }
}
