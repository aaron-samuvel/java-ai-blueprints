package io.github.aaronsamuvel.blueprints.swarm;

import io.github.aaronsamuvel.blueprints.swarm.agents.RefundDecision;

public record SwarmResult(String route, RefundDecision refund, String notification) {}
