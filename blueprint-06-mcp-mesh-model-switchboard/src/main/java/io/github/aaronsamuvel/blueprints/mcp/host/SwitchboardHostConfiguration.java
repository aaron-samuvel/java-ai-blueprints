package io.github.aaronsamuvel.blueprints.mcp.host;

import java.time.Duration;
import java.util.Map;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Offline wiring: three stub models that prefix their target name. The extraction stub returns
 * valid {@link TicketExtraction} JSON. For live use, replace them with an {@code OllamaChatModel}
 * (local) and hosted models; that wiring is not done in this repository because it needs paid API keys.
 */
@Configuration(proxyBeanMethods = false)
public class SwitchboardHostConfiguration {

    @Bean(destroyMethod = "close")
    ModelSwitchboardRouter modelSwitchboardRouter(@Value("${blueprints.switchboard.cloud-timeout:5s}") Duration cloudTimeout,
                                                  @Value("${blueprints.switchboard.fast-budget:1500ms}") Duration fastBudget,
                                                  @Value("${blueprints.switchboard.frontier-threshold:0.5}") double threshold,
                                                  @Value("${blueprints.switchboard.stub-cloud-delay:0ms}") Duration stubDelay) {
        return new ModelSwitchboardRouter(Map.of(
                ModelTarget.LOCAL_SOVEREIGN, stub("LOCAL_SOVEREIGN", Duration.ZERO),
                ModelTarget.CLOUD_FAST, stub("CLOUD_FAST", stubDelay),
                ModelTarget.CLOUD_FRONTIER, stub("CLOUD_FRONTIER", stubDelay)), cloudTimeout, fastBudget, threshold);
    }

    static StubChatModel stub(String name, Duration delay) {
        return new StubChatModel(p -> {
            if (!delay.isZero()) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String text = StubChatModel.lastText(p);
            if (text.contains("JSON")) {
                return "{\"category\":\"billing\",\"priority\":\"high\",\"summary\":\"[" + name + "] customer was charged twice\"}";
            }
            return "[" + name + "] " + text;
        });
    }
}
