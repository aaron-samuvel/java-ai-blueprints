package io.github.aaronsamuvel.blueprints.nl2sql.mesh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry: a card stays live for {@code ttl} after its last registration (agents
 * re-register as a heartbeat). Discovery orders by success rate, then p95 latency.
 */
public class LocalAgentRegistry implements AgentRegistry {

    private record Entry(MeshAgentCard card, Instant expires) {}

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public LocalAgentRegistry(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public void register(MeshAgentCard card) {
        if (card.name() == null || card.url() == null || card.intents() == null || card.intents().isEmpty()) {
            throw new IllegalArgumentException("card needs name, url and at least one intent");
        }
        entries.put(card.name(), new Entry(card, clock.instant().plus(ttl)));
    }

    @Override
    public List<MeshAgentCard> discover(String intent) {
        return live().stream().filter(c -> c.intents().contains(intent))
                .sorted(Comparator.comparingDouble((MeshAgentCard c) -> -c.sla().successRate()).thenComparingLong(c -> c.sla().p95LatencyMs()))
                .toList();
    }

    @Override
    public List<MeshAgentCard> all() {
        return live();
    }

    private List<MeshAgentCard> live() {
        Instant now = clock.instant();
        entries.values().removeIf(e -> e.expires().isBefore(now));
        return entries.values().stream().map(Entry::card).sorted(Comparator.comparing(MeshAgentCard::name)).toList();
    }
}
