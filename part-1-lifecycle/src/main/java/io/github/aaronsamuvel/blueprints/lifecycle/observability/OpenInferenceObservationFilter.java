package io.github.aaronsamuvel.blueprints.lifecycle.observability;

import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * J-AIDLC Stage 6 (semantic observability): tags Spring AI's Micrometer observations with the
 * OpenInference span kind ({@code openinference.span.kind}), so a Phoenix or Tempo backend can
 * group spans into LLM, CHAIN, TOOL and RETRIEVER steps. Spring AI already emits the GenAI
 * semantic-convention keys (model, token usage); this adds only the OpenInference classification.
 * The name-to-kind mapping is <b>author's design (not from handbook)</b>.
 */
public class OpenInferenceObservationFilter implements ObservationFilter {

    public static final String SPAN_KIND = "openinference.span.kind";

    /** Spring AI 2.0.1 observation names. */
    static final Map<String, String> KIND_BY_NAME = Map.of(
            "spring.ai.chat.client", "CHAIN",
            "spring.ai.advisor", "CHAIN",
            "gen_ai.client.operation", "LLM",
            "spring.ai.tool", "TOOL",
            "db.vector.client.operation", "RETRIEVER");

    @Override
    public Observation.Context map(Observation.Context context) {
        String kind = KIND_BY_NAME.get(context.getName());
        if (kind != null && context.getLowCardinalityKeyValue(SPAN_KIND) == null) {
            context.addLowCardinalityKeyValue(KeyValue.of(SPAN_KIND, kind));
        }
        return context;
    }
}
