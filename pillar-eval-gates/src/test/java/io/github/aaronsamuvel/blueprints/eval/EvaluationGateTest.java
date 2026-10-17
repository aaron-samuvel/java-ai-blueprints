package io.github.aaronsamuvel.blueprints.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The evaluation gate. In CI this is what blocks a merge when answers stop being grounded.
 * Thresholds: relevancy >= 90%, fact-checking >= 90% on the golden set.
 */
class EvaluationGateTest {

    static final double MIN_RELEVANCY = 0.9;
    static final double MIN_FACTS = 0.9;

    private final EvaluationGate gate = new EvaluationGate(new LexicalJudgeChatModel());

    static Stream<EvalCase> golden() {
        return EvaluationGate.load("golden-set.json").stream();
    }

    static Stream<EvalCase> regressions() {
        return EvaluationGate.load("regression-set.json").stream();
    }

    @ParameterizedTest(name = "golden {0}")
    @MethodSource("golden")
    void everyGoldenAnswerIsRelevantAndFactual(EvalCase c) {
        assertThat(gate.relevancy(c).isPass()).as("relevancy " + c.id()).isTrue();
        assertThat(gate.facts(c).isPass()).as("facts " + c.id()).isTrue();
    }

    @Test
    void goldenSetPassesTheGate() {
        GateReport report = gate.run(EvaluationGate.load("golden-set.json"));
        System.out.println("golden: " + report.summary());
        assertThat(report.passes(MIN_RELEVANCY, MIN_FACTS)).as(report.summary()).isTrue();
    }

    @ParameterizedTest(name = "regression {0}")
    @MethodSource("regressions")
    void hallucinatedAnswersFailFactChecking(EvalCase c) {
        assertThat(gate.facts(c).isPass()).as("facts " + c.id()).isFalse();
    }

    @Test
    void gateBlocksAReleaseWhoseAnswersRegressed() {
        // simulate a bad prompt change: golden set with the regressions mixed in
        List<EvalCase> mixed = Stream.concat(golden(), regressions()).toList();
        GateReport report = gate.run(mixed);
        System.out.println("mixed: " + report.summary());
        assertThat(report.passes(MIN_RELEVANCY, MIN_FACTS)).isFalse();
        assertThat(report.failures()).hasSize(3).allMatch(f -> f.contains(":facts"));
    }
}
