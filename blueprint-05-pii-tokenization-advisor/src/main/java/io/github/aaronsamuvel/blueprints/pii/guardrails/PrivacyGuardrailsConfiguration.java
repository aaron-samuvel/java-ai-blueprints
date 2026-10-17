package io.github.aaronsamuvel.blueprints.pii.guardrails;

import java.util.ArrayList;
import java.util.List;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.CompositePiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.OpenNlpPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiAnalyzerSpi;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PresidioPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.RegexPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.inspect.PrivacyInspectorService;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.BankingTools;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.PrivacyToolGuard;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.ToolPolicyRegistry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the handbook guardrails. Presidio joins the composite only when
 * {@code blueprints.environment.presidio-url} is set (the same property the aiEnvironment health
 * check probes).
 */
@Configuration(proxyBeanMethods = false)
public class PrivacyGuardrailsConfiguration {

    @Bean
    CompositePiiAnalyzer compositePiiAnalyzer(
            @Value("${blueprints.privacy.opennlp.person-model:}") String personModel,
            @Value("${blueprints.privacy.opennlp.location-model:}") String locationModel,
            @Value("${blueprints.environment.presidio-url:}") String presidioUrl,
            @Value("${blueprints.privacy.presidio.min-score:0.5}") double presidioMinScore,
            @Value("${blueprints.privacy.presidio.fail-closed:true}") boolean presidioFailClosed) {
        List<PiiAnalyzerSpi> analyzers = new ArrayList<>();
        analyzers.add(new RegexPiiAnalyzer());
        analyzers.add(new OpenNlpPiiAnalyzer(personModel, locationModel));
        if (!presidioUrl.isBlank()) analyzers.add(new PresidioPiiAnalyzer(presidioUrl, presidioMinScore, presidioFailClosed));
        return new CompositePiiAnalyzer(analyzers);
    }

    @Bean
    ToolPolicyRegistry toolPolicyRegistry(BankingTools bankingTools) {
        return new ToolPolicyRegistry().registerAnnotated(bankingTools);
    }

    @Bean
    PrivacyToolGuard privacyToolGuard(ToolPolicyRegistry registry, CompositePiiAnalyzer analyzer) {
        return new PrivacyToolGuard(registry, analyzer);
    }

    @Bean
    PrivacyInspectorService privacyInspectorService(CompositePiiAnalyzer analyzer, PrivacyToolGuard guard, BankingTools tools,
                                                    @Value("${blueprints.privacy.detokenize-authority:ROLE_PII_VIEWER}") String authority) {
        return new PrivacyInspectorService(analyzer, guard, tools, authority);
    }
}
