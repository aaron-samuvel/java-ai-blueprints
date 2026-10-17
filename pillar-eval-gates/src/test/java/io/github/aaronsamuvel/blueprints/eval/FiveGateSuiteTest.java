package io.github.aaronsamuvel.blueprints.eval;

import java.util.List;

import io.github.aaronsamuvel.blueprints.eval.gates.AnswerTokenBudgetEvaluator;
import io.github.aaronsamuvel.blueprints.eval.gates.GateSuite;
import io.github.aaronsamuvel.blueprints.eval.gates.PiiLeakEvaluator;
import io.github.aaronsamuvel.blueprints.eval.gates.StructuredOutputEvaluator;
import org.junit.jupiter.api.Test;

import org.springframework.ai.evaluation.EvaluationRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** All five gates: two from the handbook (FactChecking, Relevancy) and three of the author's design. */
class FiveGateSuiteTest {

    record RefundDecision(String orderId, Boolean eligible, String reason) {}

    private final EvaluationGate judged = new EvaluationGate(new LexicalJudgeChatModel());

    private GateSuite proseSuite() {
        return new GateSuite()
                .gate("groundedness", judged.factCheckingEvaluator())
                .gate("relevance", judged.relevancyEvaluator())
                .gate("pii-leak", new PiiLeakEvaluator())
                .gate("answer-budget", new AnswerTokenBudgetEvaluator(60));
    }

    @Test
    void goldenSetPassesEveryProseGate() {
        GateSuite.Report r = proseSuite().run(EvaluationGate.load("golden-set.json"));
        assertThat(r.failures()).isEmpty();
        assertThat(r.passed()).containsOnlyKeys("groundedness", "relevance", "pii-leak", "answer-budget");
    }

    @Test
    void piiAndRamblingAnswersFailTheirGatesEvenWhenGrounded() {
        String ctx = "Customers can request a full refund within 30 days of delivery.";
        List<EvalCase> bad = List.of(
                new EvalCase("leaks-email", "How long do I have?", List.of(ctx),
                        "You can request a full refund within 30 days of delivery; write to asha.k@example.com."),
                new EvalCase("leaks-card", "How long do I have?", List.of(ctx),
                        "Card 4111 1111 1111 1111 can request a full refund within 30 days of delivery."),
                new EvalCase("rambles", "How long do I have?", List.of(ctx),
                        ("You can request a full refund within 30 days of delivery. ").repeat(12)));
        GateSuite.Report r = proseSuite().run(bad);
        assertThat(r.failures()).contains("leaks-email:pii-leak", "leaks-card:pii-leak", "rambles:answer-budget");
        assertThat(r.failures()).doesNotContain("rambles:pii-leak", "leaks-email:answer-budget");
    }

    @Test
    void structuredOutputGateChecksParsingAndRequiredFields() {
        var gate = new StructuredOutputEvaluator<>(RefundDecision.class);
        var ok = gate.evaluate(new EvaluationRequest("q", List.of(),
                "```json\n{\"orderId\":\"ORD-7\",\"eligible\":true,\"reason\":\"within 30 days\"}\n```"));
        assertThat(ok.isPass()).as(ok.getFeedback()).isTrue();
        assertThat(gate.evaluate(new EvaluationRequest("q", List.of(), "{\"orderId\":\"ORD-7\",\"eligible\":true}")).getFeedback())
                .contains("reason");
        assertThat(gate.evaluate(new EvaluationRequest("q", List.of(), "Sure! The order is eligible.")).isPass()).isFalse();
    }

    @Test
    void luhnRejectsRandomDigitRuns() {
        assertThat(new PiiLeakEvaluator().evaluate(new EvaluationRequest("q", List.of(), "Ticket 1234-5678-9012-3456 is open")).isPass()).isTrue();
    }
}
