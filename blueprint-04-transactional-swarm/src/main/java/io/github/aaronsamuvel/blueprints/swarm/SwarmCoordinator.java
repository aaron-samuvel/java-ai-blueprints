package io.github.aaronsamuvel.blueprints.swarm;

import io.github.aaronsamuvel.blueprints.swarm.agents.RefundDecision;
import io.github.aaronsamuvel.blueprints.swarm.agents.SwarmAgents;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantContext;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the swarm as ONE database transaction: chat memory, refund row and outbox message commit
 * together or not at all (handbook pillar 5: unified data and transactions). The outbox row is
 * what a relay publishes after commit, so customers are never notified about a rolled-back refund.
 */
@Service
public class SwarmCoordinator {

    private final SwarmAgents agents;

    public SwarmCoordinator(SwarmAgents agents) {
        this.agents = agents;
    }

    /** Must be called inside {@link TenantContext#callAs}. */
    @Transactional
    public SwarmResult handle(String conversationId, String request) {
        TenantContext.require();
        String route = agents.triage(conversationId, request);
        if (!"REFUND".equals(route)) {
            return new SwarmResult(route, null, null);
        }
        RefundDecision decision = agents.refund(conversationId, request);
        String note = agents.notifyCustomer(conversationId, decision);
        return new SwarmResult(route, decision, note);
    }
}
