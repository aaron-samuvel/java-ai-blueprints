package io.github.aaronsamuvel.blueprints.pii.guardrails;

import java.util.List;
import java.util.Set;

import io.github.aaronsamuvel.blueprints.pii.guardrails.advisor.PrivacyProtectionCallAdvisor;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.CompositePiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.OpenNlpPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;
import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.RegexPiiAnalyzer;
import io.github.aaronsamuvel.blueprints.pii.guardrails.inspect.InspectorChatModel;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.GuardedToolCallback;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.PrivacyToolGuard;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.ToolPolicy;
import io.github.aaronsamuvel.blueprints.pii.guardrails.tools.ToolPolicyRegistry;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacySession;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyTokenVault;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.ResponsePolicy;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrivacyBoundaryUnitTest {

    private final CompositePiiAnalyzer analyzer = new CompositePiiAnalyzer(List.of(new RegexPiiAnalyzer(), new OpenNlpPiiAnalyzer()));

    private static UsernamePasswordAuthenticationToken caller(String... roles) {
        return UsernamePasswordAuthenticationToken.authenticated("u", null, AuthorityUtils.createAuthorityList(roles));
    }

    @Test
    void vaultPlaceholdersAreDeterministicPerRequestAndRestoreSelectively() {
        PrivacyTokenVault vault = new PrivacyTokenVault();
        String t = vault.tokenize("Priya Sharma (priya.sharma@example.com) and Rahul Verma; again Priya Sharma", analyzer);
        assertThat(t).isEqualTo("<PII:PERSON_1> (<PII:EMAIL_1>) and <PII:PERSON_2>; again <PII:PERSON_1>");
        assertThat(vault.tokenize(t, analyzer)).isEqualTo(t);

        PrivacyTokenVault.Restoration r = vault.restore(t, Set.of(PiiEntityType.EMAIL));
        assertThat(r.text()).contains("priya.sharma@example.com").contains("<PII:PERSON_1>").doesNotContain("Priya");
        assertThat(r.withheld()).containsExactly("<PII:PERSON_1>", "<PII:PERSON_2>");
        assertThat(vault.redactPlaceholders(t)).startsWith("[REDACTED_PERSON] ([REDACTED_EMAIL])");
        assertThat(new PrivacyTokenVault().tokenize("Rahul Verma", analyzer)).isEqualTo("<PII:PERSON_1>");
    }

    @Test
    void mcpStyleToolGetsOnlyItsAllowedTypesAndItsOutputIsRetokenised() {
        PrivacySession session = PrivacySession.start(caller("ROLE_COMPLIANCE_AGENT"), ResponsePolicy.REDACT);
        String acct = session.vault().placeholderFor(PiiEntityType.ACCOUNT_NUMBER, "123456789012");
        String person = session.vault().placeholderFor(PiiEntityType.PERSON, "Priya Sharma");
        // A plain ToolCallback with no Java annotations, as an MCP client would provide.
        ToolCallback mcpTool = FunctionToolCallback.builder("ledgerLookup", (Ledger in) -> "ledger for " + in.account() + " / " + in.holder()
                        + ", contact priya.sharma@example.com")
                .description("ledger").inputType(Ledger.class).build();
        ToolPolicyRegistry registry = new ToolPolicyRegistry()
                .register("ledgerLookup", new ToolPolicy(Set.of(PiiEntityType.ACCOUNT_NUMBER), "ROLE_COMPLIANCE_AGENT"));
        List<ToolCallback> visible = new PrivacyToolGuard(registry, analyzer).authorize(session, List.of(mcpTool));
        assertThat(visible).singleElement().isInstanceOf(GuardedToolCallback.class);

        String out = visible.get(0).call("{\"account\":\"" + acct + "\",\"holder\":\"" + person + "\"}");
        var d = session.audit().disclosures().get(0);
        assertThat(d.argumentsDisclosedToTool()).contains("123456789012").contains(person).doesNotContain("Priya");
        assertThat(d.withheldPlaceholders()).containsExactly(person);
        // The tool echoed the account without the "account" keyword: the vault still knows the value.
        assertThat(out).doesNotContain("123456789012").doesNotContain("priya.sharma@example.com")
                .contains(acct).contains("<PII:EMAIL_1>");

        PrivacySession other = PrivacySession.start(caller("ROLE_SUPPORT_AGENT"), ResponsePolicy.REDACT);
        assertThat(new PrivacyToolGuard(registry, analyzer).authorize(other, List.of(mcpTool))).isEmpty();
        assertThat(other.audit().toolsHidden()).containsExactly("ledgerLookup");
    }

    record Ledger(String account, String holder) {}

    @Test
    void advisorFailsClosedWithoutASession() {
        ChatClient client = ChatClient.builder(new InspectorChatModel())
                .defaultAdvisors(new PrivacyProtectionCallAdvisor(analyzer, "ROLE_PII_VIEWER", 0)).build();
        assertThatThrownBy(() -> client.prompt().user("hi Priya Sharma").call().content())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No PrivacySession");
    }
}
