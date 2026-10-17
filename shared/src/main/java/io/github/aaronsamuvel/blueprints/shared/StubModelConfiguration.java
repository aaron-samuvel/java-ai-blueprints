package io.github.aaronsamuvel.blueprints.shared;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers an echoing {@link StubChatModel} when {@code blueprints.model=stub} (the default for
 * offline runs) and no other ChatModel is present. Import it explicitly with {@code @Import}.
 */
@Configuration(proxyBeanMethods = false)
public class StubModelConfiguration {

    @Bean
    @ConditionalOnMissingBean(ChatModel.class)
    @ConditionalOnProperty(name = "blueprints.model", havingValue = "stub", matchIfMissing = true)
    StubChatModel stubChatModel() {
        return StubChatModel.echo();
    }
}
