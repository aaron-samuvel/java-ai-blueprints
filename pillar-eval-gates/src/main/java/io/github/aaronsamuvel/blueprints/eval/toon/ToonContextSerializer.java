package io.github.aaronsamuvel.blueprints.eval.toon;

import com.cedarsoftware.io.JsonIo;
import com.cedarsoftware.io.WriteOptions;
import com.cedarsoftware.io.WriteOptionsBuilder;
import io.github.aaronsamuvel.blueprints.eval.gates.AnswerTokenBudgetEvaluator;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialises Java objects for prompt context in TOON (Token-Oriented Object Notation) using
 * json-io (handbook Part IV, 10.2), and measures the token saving against compact Jackson JSON.
 * Uniform lists of records become one header row plus CSV-like rows, which is where the saving
 * comes from. The handbook's "40-50%" figure is not assumed; {@link #compare} measures it.
 */
public final class ToonContextSerializer {

    public record TokenComparison(int jsonTokens, int toonTokens) {
        public double reductionPercent() {
            return jsonTokens == 0 ? 0 : 100.0 * (jsonTokens - toonTokens) / jsonTokens;
        }
    }

    private static final WriteOptions NO_TYPES = new WriteOptionsBuilder().showTypeInfoNever().build();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ToonContextSerializer() {}

    public static String toToon(Object value) {
        return JsonIo.toToon(value, NO_TYPES);
    }

    public static String toJson(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static TokenComparison compare(Object value) {
        return new TokenComparison(AnswerTokenBudgetEvaluator.count(toJson(value)), AnswerTokenBudgetEvaluator.count(toToon(value)));
    }
}
