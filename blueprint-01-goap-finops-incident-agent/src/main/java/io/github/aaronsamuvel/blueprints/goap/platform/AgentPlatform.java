package io.github.aaronsamuvel.blueprints.goap.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.goap.planner.GoapAction;
import io.github.aaronsamuvel.blueprints.goap.planner.GoapPlanner;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.aop.support.AopUtils;

/**
 * Runs an {@link Agent} bean as an OODA loop over a typed blackboard:
 * <ol>
 *   <li><b>Observe</b>: facts = types on the blackboard + the current value of every {@link Condition};</li>
 *   <li><b>Orient/Decide</b>: A* ({@link GoapPlanner}) finds the cheapest action sequence to the goal type;</li>
 *   <li><b>Act</b>: run only the first action, put its result on the blackboard, then loop (replan).</li>
 * </ol>
 * No LLM chooses the next step. Self-contained substitute for the Embabel agent platform
 * (Embabel needs Spring Boot 4.1; this repo is on 4.0.8).
 */
public final class AgentPlatform {

    private record ActionDef(String name, Method method, List<Class<?>> inputs, Class<?> output, Set<String> pre,
                             Set<String> post, boolean canRerun, double cost, List<String> toolGroups, boolean goal) {}

    private record ConditionDef(String name, Method method, List<Class<?>> inputs) {}

    private final Map<String, ChatModel> modelsByRole;
    private final ToolGroups toolGroups;
    private final int maxSteps;
    private final GoapPlanner planner = new GoapPlanner();

    public AgentPlatform(Map<String, ChatModel> modelsByRole, ToolGroups toolGroups, int maxSteps) {
        this.modelsByRole = Map.copyOf(modelsByRole);
        this.toolGroups = toolGroups;
        this.maxSteps = maxSteps;
    }

    @SuppressWarnings("unchecked")
    public <T> AgentProcessResult<T> run(Object agent, Object... inputs) {
        Class<?> type = AopUtils.getTargetClass(agent);
        if (!type.isAnnotationPresent(Agent.class)) throw new IllegalArgumentException(type + " is not an @Agent");
        List<ActionDef> actions = actions(type);
        List<ConditionDef> conditions = conditions(type);
        ActionDef goal = actions.stream().filter(ActionDef::goal).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(type + " has no @AchievesGoal action"));

        Map<Class<?>, Object> blackboard = new LinkedHashMap<>();
        for (Object in : inputs) blackboard.put(in.getClass(), in);
        Set<String> ran = new HashSet<>();
        List<String> actionsRun = new ArrayList<>();
        List<String> trace = new ArrayList<>();
        List<LlmInvocation> calls = new CopyOnWriteArrayList<>();
        Map<String, GoapAction> planActions = actions.stream()
                .collect(Collectors.toMap(ActionDef::name, AgentPlatform::toGoap, (a, b) -> a, LinkedHashMap::new));

        for (int step = 1; step <= maxSteps; step++) {
            if (find(blackboard, goal.output()) != null) break;
            Set<String> facts = observe(agent, blackboard, conditions, ran);                       // Observe
            Optional<List<GoapAction>> plan = planner.plan(facts, Set.of(has(goal.output())), planActions.values()); // Decide
            if (plan.isEmpty() || plan.get().isEmpty()) {
                trace.add("step " + step + ": no plan reaches " + goal.output().getSimpleName() + " from " + new TreeSet<>(facts));
                break;
            }
            GoapAction next = plan.get().get(0);
            trace.add("step " + step + ": facts=" + new TreeSet<>(facts) + " plan="
                    + plan.get().stream().map(GoapAction::name).collect(Collectors.joining(">")) + " act=" + next.name());
            ActionDef def = actions.stream().filter(a -> a.name().equals(next.name())).findFirst().orElseThrow();
            Object result = invoke(agent, def, blackboard, calls);                                  // Act
            if (result != null) blackboard.put(def.output(), result);
            if (!def.canRerun()) ran.add(def.name());
            actionsRun.add(def.name());
        }
        T out = (T) find(blackboard, goal.output());
        return new AgentProcessResult<>(out != null, out, actionsRun, trace, calls);
    }

    private Set<String> observe(Object agent, Map<Class<?>, Object> bb, List<ConditionDef> conditions, Set<String> ran) {
        Set<String> facts = new HashSet<>();
        bb.keySet().forEach(c -> facts.add(has(c)));
        for (ConditionDef c : conditions) {
            Object[] args = c.inputs().stream().map(t -> find(bb, t)).toArray();
            if (Arrays.stream(args).anyMatch(a -> a == null)) {
                facts.add(c.name()); // unknown: optimistic for planning, re-checked after the next action
                continue;
            }
            boolean v = (Boolean) call(agent, c.method(), args);
            facts.add(v ? c.name() : "!" + c.name());
        }
        ran.forEach(r -> facts.add("ran:" + r));
        return facts;
    }

    private Object invoke(Object agent, ActionDef def, Map<Class<?>, Object> bb, List<LlmInvocation> calls) {
        Class<?>[] params = def.method().getParameterTypes();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = params[i] == Ai.class
                    ? new ChatClientAi(modelsByRole, def.name(), toolGroups.resolve(def.toolGroups()), LlmOptions.fast(), calls::add)
                    : find(bb, params[i]);
        }
        return call(agent, def.method(), args);
    }

    private static Object call(Object target, Method m, Object[] args) {
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object find(Map<Class<?>, Object> bb, Class<?> type) {
        Object exact = bb.get(type);
        if (exact != null) return exact;
        return bb.entrySet().stream().filter(e -> type.isAssignableFrom(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private static String has(Class<?> c) {
        return "has:" + c.getSimpleName();
    }

    private static GoapAction toGoap(ActionDef a) {
        Set<String> pre = new HashSet<>(a.pre());
        a.inputs().forEach(c -> pre.add(has(c)));
        Set<String> adds = new HashSet<>(a.post());
        adds.add(has(a.output()));
        Set<String> removes = a.post().stream().map(p -> p.startsWith("!") ? p.substring(1) : "!" + p).collect(Collectors.toSet());
        Set<String> forbidden = new HashSet<>();
        if (!a.canRerun()) {
            adds.add("ran:" + a.name());
            forbidden.add("ran:" + a.name());
        }
        return new GoapAction(a.name(), pre, forbidden, adds, removes, a.cost());
    }

    private static List<ActionDef> actions(Class<?> type) {
        List<ActionDef> out = new ArrayList<>();
        for (Method m : type.getDeclaredMethods()) {
            Action a = m.getAnnotation(Action.class);
            if (a == null) continue;
            if (m.getReturnType() == void.class) throw new IllegalArgumentException("@Action " + m.getName() + " must return a type");
            List<Class<?>> inputs = Arrays.stream(m.getParameterTypes()).filter(p -> p != Ai.class).collect(Collectors.toList());
            out.add(new ActionDef(m.getName(), m, inputs, m.getReturnType(), Set.of(a.pre()), Set.of(a.post()), a.canRerun(),
                    a.cost(), List.of(a.toolGroups()), m.isAnnotationPresent(AchievesGoal.class)));
        }
        out.sort((x, y) -> x.name().compareTo(y.name())); // deterministic tie-breaking
        return out;
    }

    private static List<ConditionDef> conditions(Class<?> type) {
        List<ConditionDef> out = new ArrayList<>();
        for (Method m : type.getDeclaredMethods()) {
            Condition c = m.getAnnotation(Condition.class);
            if (c == null) continue;
            if (m.getReturnType() != boolean.class) throw new IllegalArgumentException("@Condition " + m.getName() + " must return boolean");
            out.add(new ConditionDef(c.name().isBlank() ? m.getName() : c.name(), m, List.of(m.getParameterTypes())));
        }
        return out;
    }
}
