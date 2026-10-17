package io.github.aaronsamuvel.blueprints.eval.gates;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gate 3, schema conformance (author's design: the handbook's cover promises five evaluator
 * gates but names only FactChecking and Relevancy). The response must parse into the target
 * record, and every record component must be non-null. Deterministic, no judge model.
 */
public class StructuredOutputEvaluator<T extends Record> implements Evaluator {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Class<T> type;

    public StructuredOutputEvaluator(Class<T> type) {
        this.type = type;
    }

    @Override
    public EvaluationResponse evaluate(EvaluationRequest request) {
        String text = strip(request.getResponseContent());
        JsonNode tree;
        try {
            tree = JSON.readTree(text);
            JSON.treeToValue(tree, type); // type check: wrong value types fail here
        } catch (RuntimeException e) {
            return new EvaluationResponse(false, "not a valid " + type.getSimpleName() + ": " + e.getClass().getSimpleName(), Map.of());
        }
        List<String> missing = new ArrayList<>();
        for (RecordComponent rc : type.getRecordComponents()) {
            JsonNode field = tree.get(rc.getName());
            if (field == null || field.isNull()) missing.add(rc.getName());
        }
        return missing.isEmpty()
                ? new EvaluationResponse(true, "conforms to " + type.getSimpleName(), Map.of())
                : new EvaluationResponse(false, "missing fields " + missing, Map.of("missing", missing));
    }

    /** Models often wrap JSON in a markdown fence; accept that, as BeanOutputConverter does. */
    private static String strip(String s) {
        String t = s == null ? "" : s.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.strip();
    }
}
