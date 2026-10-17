package io.github.aaronsamuvel.blueprints.swarm.commerce;

import java.time.Clock;

import io.github.aaronsamuvel.blueprints.shared.StubEmbeddingModel;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.OrderExecutionAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.OrderTools;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.ProductDiscoveryAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.RefundAndDisputeAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.RefundPolicy;
import io.github.aaronsamuvel.blueprints.swarm.commerce.agents.TriageRouterAgent;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.OrderRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.ProductRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.RefundCaseRepository;
import io.github.aaronsamuvel.blueprints.swarm.commerce.memory.TenantPartitionedChatMemory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wires the handbook's Blueprint 4 swarm. Offline ({@code blueprints.model=stub}) every agent uses
 * {@link DemoCommerceModel}; otherwise the application's ChatModel. The product vector index is an
 * in-memory {@link SimpleVectorStore} (the handbook's Cosmos DB DiskANN index is substituted).
 */
@Configuration(proxyBeanMethods = false)
@EnableJdbcRepositories(basePackageClasses = io.github.aaronsamuvel.blueprints.swarm.commerce.domain.CommerceRepositories.class,
        considerNestedRepositories = true)
public class CommerceSwarmConfiguration {

    /**
     * Spring Data JDBC quotes identifiers by default, which makes them case-sensitive. The schema uses
     * plain unquoted names, so turn quoting off; the same mapping then works on H2 and PostgreSQL.
     */
    @Bean
    static BeanPostProcessor unquotedJdbcIdentifiers() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof RelationalMappingContext context) context.setForceQuote(false);
                return bean;
            }
        };
    }

    /** Holder, so the demo model is not a second {@code ChatModel} bean next to the app's own. */
    public record CommerceModel(ChatModel model) {}

    @Bean
    CommerceModel commerceModel(@Value("${blueprints.model:stub}") String mode, ObjectProvider<ChatModel> appModel) {
        return new CommerceModel("stub".equals(mode) ? new DemoCommerceModel() : appModel.getObject());
    }

    @Bean
    OrderTools orderTools(ProductRepository products, OrderRepository orders) {
        return new OrderTools(products, orders);
    }

    @Bean
    ProductDiscoveryAgent productDiscoveryAgent(CommerceModel commerceModel, ProductRepository products) {
        return new ProductDiscoveryAgent(ChatClient.create(commerceModel.model()),
                SimpleVectorStore.builder(new StubEmbeddingModel(256)).build(), products);
    }

    @Bean
    ApplicationRunner indexCatalogue(ProductDiscoveryAgent discovery) {
        return args -> discovery.index();
    }

    @Bean
    AgentOrchestratorService agentOrchestratorService(CommerceModel commerceModel, ProductDiscoveryAgent discovery, OrderTools tools,
                                                      OrderRepository orders, RefundCaseRepository refunds, PlatformTransactionManager txManager,
                                                      JdbcTemplate jdbc,
                                                      @Value("${blueprints.swarm.memory.max-messages:12}") int maxMessages,
                                                      @Value("${blueprints.swarm.memory.keep-recent:6}") int keepRecent,
                                                      @Value("${blueprints.swarm.refund-window-days:30}") int windowDays) {
        // Not a bean: the module's first blueprint already has a ChatMemory bean, and this one is internal to the swarm.
        TenantPartitionedChatMemory memory = new TenantPartitionedChatMemory(jdbc, maxMessages, keepRecent, null);
        ChatClient plain = ChatClient.create(commerceModel.model());
        ChatClient withTools = ChatClient.builder(commerceModel.model()).defaultAdvisors(ToolCallingAdvisor.builder().build()).build();
        return new AgentOrchestratorService(
                new TriageRouterAgent(plain),
                discovery,
                new OrderExecutionAgent(withTools, tools),
                new RefundAndDisputeAgent(plain, orders, refunds, new RefundPolicy(windowDays), new TransactionTemplate(txManager), 3, Clock.systemDefaultZone()),
                plain,
                memory);
    }
}
