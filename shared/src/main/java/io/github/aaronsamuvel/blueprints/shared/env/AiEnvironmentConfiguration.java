package io.github.aaronsamuvel.blueprints.shared.env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Import with {@code @Import(AiEnvironmentConfiguration.class)} in an app that has the actuator starter.
 * Registers {@code /actuator/health/aiEnvironment} and prints a diagnostic table at startup.
 * <p>Properties: {@code spring.ai.ollama.base-url}, {@code blueprints.environment.presidio-url},
 * {@code blueprints.environment.mcp-urls} (comma-separated), {@code blueprints.environment.startup-report}.
 * Extra probes (for example a vector store) are picked up as {@link EnvironmentProbe} beans.
 */
@Configuration(proxyBeanMethods = false)
public class AiEnvironmentConfiguration {

    private static final Log log = LogFactory.getLog(AiEnvironmentConfiguration.class);

    @Bean
    SupportingEnvironmentHealthIndicator aiEnvironmentHealthIndicator(
            ObjectProvider<ChatModel> chat, ObjectProvider<EmbeddingModel> embedding, ObjectProvider<EnvironmentProbe> extra,
            @Value("${spring.ai.ollama.base-url:}") String ollamaBaseUrl,
            @Value("${blueprints.environment.presidio-url:}") String presidioUrl,
            @Value("${blueprints.environment.mcp-urls:}") String mcpUrls) {
        List<EnvironmentProbe> probes = new ArrayList<>();
        probes.add(EnvironmentProbes.chatModel(chat::getIfUnique, ollamaBaseUrl));
        probes.add(EnvironmentProbes.embeddingModel(embedding::getIfUnique));
        extra.orderedStream().forEach(probes::add);
        probes.add(EnvironmentProbes.httpHealth("presidio", presidioUrl.isBlank() ? "" : presidioUrl.replaceAll("/+$", "") + "/health"));
        probes.addAll(EnvironmentProbes.mcpServers(Arrays.stream(mcpUrls.split(",")).map(String::strip).toList()));
        return new SupportingEnvironmentHealthIndicator(probes);
    }

    @Bean
    @ConditionalOnProperty(name = "blueprints.environment.startup-report", havingValue = "true", matchIfMissing = true)
    ApplicationRunner aiEnvironmentStartupReport(SupportingEnvironmentHealthIndicator indicator) {
        return args -> log.info("AI supporting environment:\n" + SupportingEnvironmentHealthIndicator.table(indicator.runProbes()));
    }
}
