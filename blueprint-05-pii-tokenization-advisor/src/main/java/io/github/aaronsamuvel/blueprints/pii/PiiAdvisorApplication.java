package io.github.aaronsamuvel.blueprints.pii;

import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;
import io.github.aaronsamuvel.blueprints.shared.env.AiEnvironmentConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({StubModelConfiguration.class, AiEnvironmentConfiguration.class})
public class PiiAdvisorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PiiAdvisorApplication.class, args);
    }

    @Bean
    PiiTokenizer piiTokenizer() {
        return new PiiTokenizer();
    }
}
