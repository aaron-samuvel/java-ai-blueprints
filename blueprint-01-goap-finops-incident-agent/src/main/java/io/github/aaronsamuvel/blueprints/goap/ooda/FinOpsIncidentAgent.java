package io.github.aaronsamuvel.blueprints.goap.ooda;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.goap.planner.GoapAction;
import io.github.aaronsamuvel.blueprints.goap.planner.GoapPlanner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * FinOps incident agent: an OODA loop whose Decide step is a GOAP planner.
 * <ul>
 *   <li><b>Observe</b> the current world-state facts (what is known so far).</li>
 *   <li><b>Orient</b>: actions such as {@code diagnose} add new facts (for example {@code cause:RUNAWAY_JOB}),
 *       using an LLM with a typed {@link CostDiagnosis} contract.</li>
 *   <li><b>Decide</b>: re-plan the cheapest action sequence from the <i>current</i> facts to the goal.</li>
 *   <li><b>Act</b>: execute only the first action, then loop. New facts can change the plan.</li>
 * </ul>
 */
public class FinOpsIncidentAgent {

    public static final String GOAL = "incidentClosed";

    private final ChatClient chat;
    private final GoapPlanner planner = new GoapPlanner();
    private final BigDecimal anomalyFactor;
    private final BigDecimal autoApproveLimitInr;
    private final int maxCycles;

    public FinOpsIncidentAgent(ChatModel model, BigDecimal anomalyFactor, BigDecimal autoApproveLimitInr, int maxCycles) {
        this.chat = ChatClient.create(model);
        this.anomalyFactor = anomalyFactor;
        this.autoApproveLimitInr = autoApproveLimitInr;
        this.maxCycles = maxCycles;
    }

    /** Mutable working memory for one run. */
    private static final class Run {
        final Set<String> facts = new HashSet<>();
        final List<String> trace = new ArrayList<>();
        String service;
        BigDecimal today = BigDecimal.ZERO, baseline = BigDecimal.ZERO;
        CostDiagnosis.Cause cause = CostDiagnosis.Cause.UNKNOWN;
        IncidentReport.Outcome outcome;
    }

    /**
     * {@code applyDeclared=false} marks a sensing action whose declared effects are an optimistic
     * expectation for planning only; its executor writes the facts that actually happened.
     */
    private record Step(GoapAction action, Consumer<Run> execute, boolean applyDeclared) {}

    public IncidentReport run(List<CostSample> billing) {
        Run r = new Run();
        Map<String, Step> steps = steps(billing);
        List<GoapAction> actions = steps.values().stream().map(Step::action).toList();

        for (int cycle = 1; cycle <= maxCycles; cycle++) {
            if (r.facts.contains(GOAL)) break;
            Optional<List<GoapAction>> plan = planner.plan(r.facts, Set.of(GOAL), actions);    // Decide
            if (plan.isEmpty() || plan.get().isEmpty()) {
                r.trace.add("cycle " + cycle + ": no plan from " + new java.util.TreeSet<>(r.facts));
                break;
            }
            GoapAction next = plan.get().get(0);
            r.trace.add("cycle " + cycle + ": plan=" + plan.get().stream().map(GoapAction::name).collect(Collectors.joining(">"))
                    + " act=" + next.name());
            Step step = steps.get(next.name());
            step.execute().accept(r);                                                           // Act (+ Observe/Orient facts)
            if (step.applyDeclared()) {
                r.facts.addAll(next.adds());
                r.facts.removeAll(next.removes());
            }
        }
        IncidentReport.Outcome outcome = r.outcome != null ? r.outcome : IncidentReport.Outcome.ESCALATED;
        return new IncidentReport(outcome, r.service, r.today, r.baseline, r.cause, r.trace);
    }

    private Map<String, Step> steps(List<CostSample> billing) {
        Map<String, Step> m = new java.util.LinkedHashMap<>();
        // Observe: compare each service's latest day with its average over the earlier days.
        add(m, GoapAction.of("observeBilling", Set.of(), Set.of("billingObserved"), 1).unless("billingObserved"), r -> {
            var byService = billing.stream().collect(Collectors.groupingBy(CostSample::service));
            var worst = byService.entrySet().stream().map(e -> {
                List<CostSample> rows = e.getValue().stream().sorted(Comparator.comparing(CostSample::day)).toList();
                CostSample last = rows.get(rows.size() - 1);
                BigDecimal base = rows.subList(0, rows.size() - 1).stream().map(CostSample::costInr)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(Math.max(1, rows.size() - 1)), 2, RoundingMode.HALF_UP);
                return Map.entry(e.getKey(), new BigDecimal[] {last.costInr(), base});
            }).max(Comparator.comparing(e -> e.getValue()[0].subtract(e.getValue()[1]))).orElseThrow();
            r.service = worst.getKey();
            r.today = worst.getValue()[0];
            r.baseline = worst.getValue()[1];
            boolean anomaly = r.today.compareTo(r.baseline.multiply(anomalyFactor)) > 0;
            r.facts.add(anomaly ? "anomaly" : "noAnomaly");
        });
        add(m, GoapAction.of("closeNoIncident", Set.of("noAnomaly"), Set.of(GOAL), 1), r -> r.outcome = IncidentReport.Outcome.NO_INCIDENT);
        // Orient: LLM diagnosis (the planner picks closeNoIncident instead whenever 'noAnomaly' was observed,
        // because that path is far cheaper than diagnose + any remedy). with a typed contract; a bad answer degrades to UNKNOWN, never crashes.
        add(m, GoapAction.of("diagnose", Set.of("billingObserved"), Set.of("diagnosed"), 2).unless("diagnosed"), r -> {
            BillingFacts f = new BillingFacts(r.service, billing);
            CostDiagnosis d;
            try {
                d = chat.prompt()
                        .system("You are a FinOps analyst. Classify the cost spike cause as IDLE_RESOURCES, RUNAWAY_JOB or UNKNOWN.")
                        .user(f.describe()).call().entity(CostDiagnosis.class);
            } catch (RuntimeException e) {
                d = null;
            }
            r.cause = d == null || d.cause() == null ? CostDiagnosis.Cause.UNKNOWN : d.cause();
            r.facts.add("cause:" + r.cause);
            r.trace.add("  diagnosis=" + r.cause);
        });
        add(m, GoapAction.of("scaleDownIdle", Set.of("cause:IDLE_RESOURCES"), Set.of("savingsApplied"), 2), r -> r.trace.add("  scaled down idle capacity of " + r.service));
        add(m, GoapAction.of("requestApproval", Set.of("cause:RUNAWAY_JOB"), Set.of("approvalDecided", "approved"), 1).unless("approvalDecided"), r -> {
            r.facts.add("approvalDecided");
            BigDecimal excess = r.today.subtract(r.baseline);
            boolean ok = excess.compareTo(autoApproveLimitInr) <= 0;
            r.facts.add(ok ? "approved" : "denied");
            r.trace.add("  approval " + (ok ? "auto-granted" : "denied") + " for excess INR " + excess);
        }, false);
        add(m, GoapAction.of("killRunawayJob", Set.of("cause:RUNAWAY_JOB", "approved"), Set.of("savingsApplied"), 1), r -> r.trace.add("  stopped runaway job on " + r.service));
        add(m, GoapAction.of("notifyOwner", Set.of("savingsApplied"), Set.of(GOAL), 1), r -> {
            r.outcome = IncidentReport.Outcome.RESOLVED;
            r.trace.add("  notified owner of " + r.service);
        });
        // Expensive fallback: the planner only picks it when nothing cheaper reaches the goal.
        add(m, GoapAction.of("escalateToHuman", Set.of("diagnosed"), Set.of(GOAL), 10), r -> {
            r.outcome = IncidentReport.Outcome.ESCALATED;
            r.trace.add("  escalated to on-call FinOps engineer");
        });
        return m;
    }

    private static void add(Map<String, Step> m, GoapAction a, Consumer<Run> exec) {
        add(m, a, exec, true);
    }

    private static void add(Map<String, Step> m, GoapAction a, Consumer<Run> exec, boolean applyDeclared) {
        m.put(a.name(), new Step(a, exec, applyDeclared));
    }

    private record BillingFacts(String service, List<CostSample> rows) {
        String describe() {
            return rows.stream().filter(s -> s.service().equals(service)).sorted(Comparator.comparing(CostSample::day))
                    .map(s -> s.day() + " cost=" + s.costInr() + " INR cpu=" + Math.round(s.cpuUtilisation() * 100) + "%")
                    .collect(Collectors.joining("\n", "Service " + service + " daily spend:\n", ""));
        }
    }
}
