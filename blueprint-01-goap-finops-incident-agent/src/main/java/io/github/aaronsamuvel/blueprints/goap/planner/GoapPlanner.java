package io.github.aaronsamuvel.blueprints.goap.planner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* search over world states (sets of true facts). Heuristic: number of unmet goal facts times
 * the cheapest action cost, which never overestimates, so the plan found is the cheapest one.
 * Returns empty when the goal is unreachable from the current state.
 */
public final class GoapPlanner {

    private record Node(Set<String> state, double g, double f, Node parent, GoapAction via) {}

    private final int maxExpansions;

    public GoapPlanner() {
        this(10_000);
    }

    public GoapPlanner(int maxExpansions) {
        this.maxExpansions = maxExpansions;
    }

    public Optional<List<GoapAction>> plan(Set<String> start, Set<String> goal, Collection<GoapAction> actions) {
        double minCost = actions.stream().mapToDouble(GoapAction::cost).min().orElse(1);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<Set<String>, Double> best = new HashMap<>();
        Set<String> s0 = Set.copyOf(start);
        open.add(new Node(s0, 0, h(s0, goal, minCost), null, null));
        best.put(s0, 0.0);
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < maxExpansions) {
            Node n = open.poll();
            if (n.state().containsAll(goal)) return Optional.of(path(n));
            if (n.g() > best.getOrDefault(n.state(), Double.MAX_VALUE)) continue;
            for (GoapAction a : actions) {
                if (!a.applicable(n.state())) continue;
                Set<String> next = a.apply(n.state());
                if (next.equals(n.state())) continue;
                double g = n.g() + a.cost();
                if (g < best.getOrDefault(next, Double.MAX_VALUE)) {
                    best.put(next, g);
                    open.add(new Node(next, g, g + h(next, goal, minCost), n, a));
                }
            }
        }
        return Optional.empty();
    }

    private static double h(Set<String> state, Set<String> goal, double minCost) {
        return goal.stream().filter(f -> !state.contains(f)).count() * minCost;
    }

    private static List<GoapAction> path(Node n) {
        List<GoapAction> out = new ArrayList<>();
        for (Node c = n; c.via() != null; c = c.parent()) out.add(c.via());
        Collections.reverse(out);
        return out;
    }
}
