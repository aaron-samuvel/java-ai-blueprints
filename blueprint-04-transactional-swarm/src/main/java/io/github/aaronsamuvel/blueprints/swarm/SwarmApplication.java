package io.github.aaronsamuvel.blueprints.swarm;

import java.math.BigDecimal;

import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;
import io.github.aaronsamuvel.blueprints.swarm.agents.SwarmAgents;
import io.github.aaronsamuvel.blueprints.swarm.memory.TenantJdbcChatMemoryRepository;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@Import(StubModelConfiguration.class)
public class SwarmApplication {

    public static void main(String[] args) {
        SpringApplication.run(SwarmApplication.class, args);
    }

    @Bean
    TenantJdbcChatMemoryRepository tenantChatMemoryRepository(JdbcTemplate jdbc) {
        return new TenantJdbcChatMemoryRepository(jdbc);
    }

    @Bean
    ChatMemory chatMemory(TenantJdbcChatMemoryRepository repo) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(repo).maxMessages(20).build();
    }

    @Bean
    SwarmAgents swarmAgents(ChatModel model, ChatMemory memory, JdbcTemplate jdbc,
                            @Value("${blueprints.swarm.max-refund-inr:5000}") BigDecimal maxRefund) {
        return new SwarmAgents(model, memory, jdbc, maxRefund);
    }
}
