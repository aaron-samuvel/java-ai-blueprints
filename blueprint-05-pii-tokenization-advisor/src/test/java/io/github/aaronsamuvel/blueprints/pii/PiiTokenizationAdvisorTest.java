package io.github.aaronsamuvel.blueprints.pii;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aaronsamuvel.blueprints.pii.advisor.PiiTokenizationAdvisor;
import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class PiiTokenizationAdvisorTest {

    private static final String EMAIL = "priya.sharma@example.com";
    private static final String PHONE = "+91 98765 43210";

    @Test
    void rawPiiNeverReachesTheModelButUserGetsItBack() {
        StubChatModel model = StubChatModel.echo();
        ChatClient client = ChatClient.builder(model)
                .defaultSystem("Customer email on file: " + EMAIL)
                .defaultAdvisors(new PiiTokenizationAdvisor(new PiiTokenizer()))
                .build();

        String answer = client.prompt().user("Call me on " + PHONE + " or mail " + EMAIL).call().content();

        String sent = model.allSentText();
        assertThat(sent).doesNotContain(EMAIL).doesNotContain(PHONE).doesNotContain("98765");
        assertThat(sent).contains("<<EMAIL_1>>").contains("<<PHONE_1>>");
        // echo model repeated the tokens; the advisor restored the real values for the user
        assertThat(answer).isEqualTo("echo: Call me on " + PHONE + " or mail " + EMAIL);
    }

    @Test
    void laterAdvisorsOnlySeeTokens() {
        StringBuilder seenByLogger = new StringBuilder();
        org.springframework.ai.chat.client.advisor.api.CallAdvisor logger =
                new org.springframework.ai.chat.client.advisor.api.CallAdvisor() {
                    public org.springframework.ai.chat.client.ChatClientResponse adviseCall(
                            org.springframework.ai.chat.client.ChatClientRequest r,
                            org.springframework.ai.chat.client.advisor.api.CallAdvisorChain c) {
                        seenByLogger.append(r.prompt().getContents());
                        return c.nextCall(r);
                    }
                    public String getName() { return "logger"; }
                    public int getOrder() { return 0; }
                };
        ChatClient client = ChatClient.builder(StubChatModel.echo())
                .defaultAdvisors(logger, new PiiTokenizationAdvisor(new PiiTokenizer()))
                .build();
        client.prompt().user("PAN ABCDE1234F").call().content();
        assertThat(seenByLogger.toString()).doesNotContain("ABCDE1234F").contains("<<PAN_1>>");
    }
}
