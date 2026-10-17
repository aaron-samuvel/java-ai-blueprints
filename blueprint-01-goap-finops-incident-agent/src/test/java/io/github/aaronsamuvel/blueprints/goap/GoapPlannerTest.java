package io.github.aaronsamuvel.blueprints.goap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import io.github.aaronsamuvel.blueprints.goap.planner.GoapAction;
import io.github.aaronsamuvel.blueprints.goap.planner.GoapPlanner;

import org.junit.jupiter.api.Test;

class GoapPlannerTest {

    private final GoapPlanner planner = new GoapPlanner();

    private static List<String> names(List<GoapAction> plan) {
        return plan.stream().map(GoapAction::name).toList();
    }

    @Test
    void findsCheapestOfTwoRoutes() {
        List<GoapAction> actions = List.of(
                GoapAction.of("a", Set.of(), Set.of("x"), 1),
                GoapAction.of("b", Set.of("x"), Set.of("goal"), 1),
                GoapAction.of("expensive", Set.of(), Set.of("goal"), 5));
        assertThat(names(planner.plan(Set.of(), Set.of("goal"), actions).orElseThrow())).containsExactly("a", "b");
    }

    @Test
    void respectsPreconditionsAndRemovals() {
        List<GoapAction> actions = List.of(
                new GoapAction("unlock", Set.of("hasKey"), Set.of("doorOpen"), Set.of("hasKey"), 1),
                GoapAction.of("getKey", Set.of(), Set.of("hasKey"), 2),
                GoapAction.of("enter", Set.of("doorOpen"), Set.of("inside"), 1));
        assertThat(names(planner.plan(Set.of(), Set.of("inside"), actions).orElseThrow())).containsExactly("getKey", "unlock", "enter");
    }

    @Test
    void negativePreconditionsBlockRepeats() {
        GoapAction ask = GoapAction.of("ask", Set.of(), Set.of("asked", "yes"), 1).unless("asked");
        assertThat(ask.applicable(Set.of())).isTrue();
        assertThat(ask.applicable(Set.of("asked"))).isFalse();
    }

    @Test
    void emptyPlanWhenGoalAlreadyHoldsAndNoneWhenUnreachable() {
        List<GoapAction> actions = List.of(GoapAction.of("a", Set.of("never"), Set.of("goal"), 1));
        assertThat(planner.plan(Set.of("goal"), Set.of("goal"), actions)).contains(List.of());
        assertThat(planner.plan(Set.of(), Set.of("goal"), actions)).isEmpty();
    }
}
