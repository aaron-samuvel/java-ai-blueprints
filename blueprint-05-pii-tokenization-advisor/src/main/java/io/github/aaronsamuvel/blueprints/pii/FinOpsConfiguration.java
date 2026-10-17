package io.github.aaronsamuvel.blueprints.pii;

import io.github.aaronsamuvel.blueprints.finops.FinOpsBudgetAdvisor;
import io.github.aaronsamuvel.blueprints.finops.PriceList;
import io.github.aaronsamuvel.blueprints.finops.TokenBudgetLedger;
import io.github.aaronsamuvel.blueprints.finops.TokenEstimator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the pillar-finops-budget advisor into this app. The in-memory ledger suits one instance;
 * swap in JdbcTokenBudgetLedger or RedisTokenBudgetLedger (both implement TokenBudgetAuthority)
 * when running several.
 */
@Configuration(proxyBeanMethods = false)
class FinOpsConfiguration {

    @Bean
    TokenBudgetLedger tokenBudgetLedger(@Value("${blueprint.finops.tenant-token-limit:200000}") long limit) {
        return new TokenBudgetLedger(limit);
    }

    @Bean
    FinOpsBudgetAdvisor finOpsBudgetAdvisor(TokenBudgetLedger ledger,
            @Value("${blueprint.finops.max-output-tokens:512}") int maxOutputTokens) {
        return new FinOpsBudgetAdvisor(ledger, new TokenEstimator(), maxOutputTokens, PriceList.FREE);
    }
}
