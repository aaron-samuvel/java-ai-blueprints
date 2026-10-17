package io.github.aaronsamuvel.blueprints.eval;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs Spring AI's {@link RelevancyEvaluator} and {@link FactCheckingEvaluator} over a golden set,
 * with any {@link ChatModel} as judge: {@link LexicalJudgeChatModel} offline, Ollama under -Pit.
 */
public class EvaluationGate {

    private final RelevancyEvaluator relevancy;
    private final FactCheckingEvaluator facts;

    public EvaluationGate(ChatModel judge) {
        this.relevancy = RelevancyEvaluator.builder().chatClientBuilder(ChatClient.builder(judge)).build();
        this.facts = FactCheckingEvaluator.builder(ChatClient.builder(judge)).build();
    }

    public EvaluationResponse relevancy(EvalCase c) {
        return relevancy.evaluate(new EvaluationRequest(c.question(), docs(c), c.answer()));
    }

    public EvaluationResponse facts(EvalCase c) {
        return facts.evaluate(new EvaluationRequest(c.question(), docs(c), c.answer()));
    }

    public GateReport run(List<EvalCase> cases) {
        int rel = 0, fact = 0;
        List<String> failures = new ArrayList<>();
        for (EvalCase c : cases) {
            boolean r = relevancy(c).isPass();
            boolean f = facts(c).isPass();
            if (r) rel++;
            if (f) fact++;
            if (!r || !f) failures.add(c.id() + (r ? "" : ":relevancy") + (f ? "" : ":facts"));
        }
        return new GateReport(cases.size(), rel, fact, failures);
    }

    public static List<EvalCase> load(String classpathJson) {
        try (InputStream in = EvaluationGate.class.getClassLoader().getResourceAsStream(classpathJson)) {
            if (in == null) throw new IllegalArgumentException("not found: " + classpathJson);
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<EvalCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Document> docs(EvalCase c) {
        return c.context().stream().map(Document::new).toList();
    }
}
