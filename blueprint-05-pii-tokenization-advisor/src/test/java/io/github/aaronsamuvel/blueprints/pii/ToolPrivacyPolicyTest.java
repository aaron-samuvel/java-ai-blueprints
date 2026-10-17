package io.github.aaronsamuvel.blueprints.pii;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.pii.core.TokenVault;
import io.github.aaronsamuvel.blueprints.pii.tools.CustomerTools;
import io.github.aaronsamuvel.blueprints.pii.tools.PrivacyEnforcingToolCallback;
import io.github.aaronsamuvel.blueprints.pii.tools.ToolPrivacyPolicy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class ToolPrivacyPolicyTest {

    private final PiiTokenizer tokenizer = new PiiTokenizer();
    private final TokenVault vault = new TokenVault();
    private final Map<String, PrivacyEnforcingToolCallback> tools =
            PrivacyEnforcingToolCallback.wrap(tokenizer, vault, new CustomerTools()).stream()
                    .map(PrivacyEnforcingToolCallback.class::cast)
                    .collect(Collectors.toMap(t -> t.getToolDefinition().name(), Function.identity()));

    @Test
    void policiesAreReadFromAnnotations() {
        assertThat(tools.get("customerProfile").mode()).isEqualTo(ToolPrivacyPolicy.Mode.TOKENIZE);
        assertThat(tools.get("kycSummary").mode()).isEqualTo(ToolPrivacyPolicy.Mode.REDACT);
        assertThat(tools.get("openTickets").mode()).isEqualTo(ToolPrivacyPolicy.Mode.ALLOW_RAW);
    }

    @Test
    void tokenizeToolOutputHidesPiiButIsReversible() {
        String out = tools.get("customerProfile").call("{\"customerId\":\"C-1001\"}");
        assertThat(out).doesNotContain("priya.sharma@example.com").doesNotContain("ABCDE1234F")
                .contains("<<EMAIL_1>>").contains("<<PAN_1>>");
        assertThat(tokenizer.detokenize(out, vault)).contains("priya.sharma@example.com");
    }

    @Test
    void redactToolOutputIsIrreversible() {
        String out = tools.get("kycSummary").call("{\"customerId\":\"C-1001\"}");
        assertThat(out).contains("[REDACTED_PAN]").doesNotContain("ABCDE1234F");
        assertThat(vault.size()).isZero();
    }

    @Test
    void allowRawPassesThrough() {
        assertThat(tools.get("openTickets").call("{\"customerId\":\"C-1001\"}")).isEqualTo("2");
    }

    @Test
    void allToolsAreWrapped() {
        List<ToolCallback> all = PrivacyEnforcingToolCallback.wrap(tokenizer, vault, new CustomerTools());
        assertThat(all).hasSize(3).allMatch(PrivacyEnforcingToolCallback.class::isInstance);
    }
}
