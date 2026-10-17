package io.github.aaronsamuvel.blueprints.lifecycle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.aaronsamuvel.blueprints.lifecycle.examples.AuditTrailAdvisor;
import io.github.aaronsamuvel.blueprints.lifecycle.observability.ObservedChatClients;
import io.github.aaronsamuvel.blueprints.lifecycle.observability.OpenInferenceObservationFilter;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityTest {

    @Test
    void chatClientAndCustomAdvisorEmitObservationsTaggedWithOpenInferenceKinds() {
        List<Observation.Context> stopped = new CopyOnWriteArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override public boolean supportsContext(Observation.Context c) { return true; }
            @Override public void onStop(Observation.Context c) { stopped.add(c); }
        });

        ObservedChatClients.builder(StubChatModel.echo(), registry).defaultAdvisors(new AuditTrailAdvisor()).build()
                .prompt().user("p95 latency on checkout").call().content();

        assertThat(stopped).extracting(Observation.Context::getName).contains("spring.ai.chat.client", "spring.ai.advisor");
        Observation.Context client = stopped.stream().filter(c -> c.getName().equals("spring.ai.chat.client")).findFirst().orElseThrow();
        assertThat(client.getLowCardinalityKeyValue(OpenInferenceObservationFilter.SPAN_KIND).getValue()).isEqualTo("CHAIN");
        assertThat(stopped).filteredOn(c -> c.getName().equals("spring.ai.advisor"))
                .extracting(c -> c.getLowCardinalityKeyValue("spring.ai.advisor.name").getValue())
                .contains("AuditTrailAdvisor");
    }

    @Test
    void modelToolAndVectorObservationsMapToLlmToolRetriever() {
        var filter = new OpenInferenceObservationFilter();
        for (var e : List.of(List.of("gen_ai.client.operation", "LLM"), List.of("spring.ai.tool", "TOOL"),
                List.of("db.vector.client.operation", "RETRIEVER"))) {
            Observation.Context c = new Observation.Context();
            c.setName(e.get(0));
            assertThat(filter.map(c).getLowCardinalityKeyValue(OpenInferenceObservationFilter.SPAN_KIND).getValue()).isEqualTo(e.get(1));
        }
        Observation.Context other = new Observation.Context();
        other.setName("http.server.requests");
        assertThat(filter.map(other).getLowCardinalityKeyValue(OpenInferenceObservationFilter.SPAN_KIND)).isNull();
    }
}
