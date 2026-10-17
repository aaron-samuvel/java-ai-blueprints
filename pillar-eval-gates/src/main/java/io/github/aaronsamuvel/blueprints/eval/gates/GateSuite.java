package io.github.aaronsamuvel.blueprints.eval.gates;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.eval.EvalCase;

import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.Evaluator;

/**
 * Runs named {@link Evaluator}s over a golden set and reports a pass rate per gate. The full
 * suite is: (1) groundedness, Spring AI {@code FactCheckingEvaluator}; (2) relevance,
 * {@code RelevancyEvaluator}; (3) {@link StructuredOutputEvaluator}; (4) {@link PiiLeakEvaluator};
 * (5) {@link AnswerTokenBudgetEvaluator}. Gates 1-2 are from the handbook, 3-5 are author's design.
 */
public class GateSuite {

    public record Report(int total, Map<String, Integer> passed, List<String> failures) {
        public Report {
            passed = Map.copyOf(passed);
            failures = List.copyOf(failures);
        }

        public double rate(String gate) {
            return total == 0 ? 1.0 : (double) passed.getOrDefault(gate, 0) / total;
        }
    }

    private final Map<String, Evaluator> gates = new LinkedHashMap<>();

    public GateSuite gate(String name, Evaluator evaluator) {
        gates.put(name, evaluator);
        return this;
    }

    public List<String> names() {
        return List.copyOf(gates.keySet());
    }

    public Report run(List<EvalCase> cases) {
        Map<String, Integer> passed = new LinkedHashMap<>();
        gates.keySet().forEach(g -> passed.put(g, 0));
        List<String> failures = new ArrayList<>();
        for (EvalCase c : cases) {
            EvaluationRequest req = new EvaluationRequest(c.question(), c.context().stream().map(Document::new).toList(), c.answer());
            gates.forEach((name, gate) -> {
                if (gate.evaluate(req).isPass()) passed.merge(name, 1, Integer::sum);
                else failures.add(c.id() + ":" + name);
            });
        }
        return new Report(cases.size(), passed, failures);
    }
}
