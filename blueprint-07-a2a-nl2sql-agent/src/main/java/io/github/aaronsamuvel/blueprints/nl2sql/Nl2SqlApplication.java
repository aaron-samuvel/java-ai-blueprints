package io.github.aaronsamuvel.blueprints.nl2sql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import io.github.aaronsamuvel.blueprints.nl2sql.agent.Nl2SqlAgent;
import io.github.aaronsamuvel.blueprints.nl2sql.guard.SqlGuard;
import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@Import(StubModelConfiguration.class)
public class Nl2SqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nl2SqlApplication.class, args);
    }

    @Bean
    SqlGuard sqlGuard(@Value("${blueprints.nl2sql.max-rows:500}") long maxRows) {
        return new SqlGuard(maxRows, Set.of("CUSTOMERS", "ORDERS"));
    }

    @Bean
    Nl2SqlAgent nl2SqlAgent(ChatModel model, SqlGuard guard, JdbcTemplate jdbc,
                            @Value("${blueprints.nl2sql.max-retries:3}") int maxRetries,
                            @Value("${blueprints.nl2sql.max-rows:500}") int maxRows) throws IOException {
        jdbc.setMaxRows(maxRows); // defence in depth on top of the guard
        jdbc.setQueryTimeout(10);
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        return new Nl2SqlAgent(model, guard, jdbc, schema, maxRetries);
    }
}
