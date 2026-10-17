package io.github.aaronsamuvel.blueprints.eval.gates;

import java.util.Map;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * Gate 5, answer cost (author's design). Fails when the answer exceeds a token ceiling, which
 * catches prompt changes that make the model ramble (and spend). Counts with JTokkit cl100k, the
 * same estimator family the FinOps pillar uses.
 */
public class AnswerTokenBudgetEvaluator implements Evaluator {

    private static final Encoding CL100K = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    private final int maxTokens;

    public AnswerTokenBudgetEvaluator(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public static int count(String text) {
        return text == null ? 0 : CL100K.countTokens(text);
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        int n = count(request.getResponseContent());
        return new EvaluationResponse(n <= maxTokens, n + " tokens (max " + maxTokens + ")", Map.of("tokens", n));
    }
}
