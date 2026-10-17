package io.github.aaronsamuvel.blueprints.goap;

import java.math.BigDecimal;

import io.github.aaronsamuvel.blueprints.goap.ooda.FinOpsIncidentAgent;
import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(StubModelConfiguration.class)
public class GoapApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoapApplication.class, args);
    }

    @Bean
    FinOpsIncidentAgent finOpsIncidentAgent(ChatModel model,
                                            @Value("${blueprints.goap.anomaly-factor:1.5}") BigDecimal factor,
                                            @Value("${blueprints.goap.auto-approve-limit-inr:10000}") BigDecimal limit) {
        return new FinOpsIncidentAgent(model, factor, limit, 10);
    }
}
