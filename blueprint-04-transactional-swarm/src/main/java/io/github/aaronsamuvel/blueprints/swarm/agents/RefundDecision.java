package io.github.aaronsamuvel.blueprints.swarm.agents;

import java.math.BigDecimal;

/** Structured output of the refund agent. */
public record RefundDecision(String orderId, BigDecimal amountInr, String reason) {}
