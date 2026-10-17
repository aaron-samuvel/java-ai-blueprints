package io.github.aaronsamuvel.blueprints.lifecycle.observability;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Builds a {@link ChatClient} that reports to an {@link ObservationRegistry}. Every call then produces
 * a {@code spring.ai.chat.client} observation and one {@code spring.ai.advisor} child per advisor.
 * With Spring Boot this happens automatically through the auto-configured {@code ChatClient.Builder}.
 * This factory is for plain Java and tests. Export to OTLP (Grafana LGTM, Phoenix) is configured
 * with {@code management.opentelemetry.tracing.export.otlp.endpoint} (see README).
 */
public final class ObservedChatClients {

    private ObservedChatClients() {}

    public static ChatClient.Builder builder(ChatModel model, ObservationRegistry registry) {
        registry.observationConfig().observationFilter(new OpenInferenceObservationFilter());
        return ChatClient.builder(model, registry, null, null);
    }
}
