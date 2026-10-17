package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.io.Serializable;

/** The reviewer's decision, written into the paused thread before it is resumed. */
public record HumanVerdict(boolean approved, String reviewer, String comment) implements Serializable {
}
