package io.github.aaronsamuvel.blueprints.goap.planner;

import java.util.HashSet;
import java.util.Set;

/**
 * A GOAP action: applicable when all {@code preconditions} hold and none of {@code forbidden}
 * (negative preconditions) do; applying it adds {@code adds},
 * removes {@code removes}. {@code cost} drives the planner towards the cheapest plan.
 * The planner only reasons about these declarations; execution lives elsewhere.
 */
public record GoapAction(String name, Set<String> preconditions, Set<String> forbidden, Set<String> adds, Set<String> removes, double cost) {

    public GoapAction {
        preconditions = Set.copyOf(preconditions);
        forbidden = Set.copyOf(forbidden);
        adds = Set.copyOf(adds);
        removes = Set.copyOf(removes);
        if (cost <= 0) throw new IllegalArgumentException("cost must be positive");
    }

    public GoapAction(String name, Set<String> preconditions, Set<String> adds, Set<String> removes, double cost) {
        this(name, preconditions, Set.of(), adds, removes, cost);
    }

    public static GoapAction of(String name, Set<String> pre, Set<String> adds, double cost) {
        return new GoapAction(name, pre, Set.of(), adds, Set.of(), cost);
    }

    /** Same action that is only applicable while none of {@code facts} hold. */
    public GoapAction unless(String... facts) {
        return new GoapAction(name, preconditions, Set.of(facts), adds, removes, cost);
    }

    public boolean applicable(Set<String> state) {
        return state.containsAll(preconditions) && forbidden.stream().noneMatch(state::contains);
    }

    public Set<String> apply(Set<String> state) {
        Set<String> next = new HashSet<>(state);
        next.removeAll(removes);
        next.addAll(adds);
        return Set.copyOf(next);
    }
}
