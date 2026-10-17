package io.github.aaronsamuvel.blueprints.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import io.github.aaronsamuvel.blueprints.lifecycle.examples.AuditTrailAdvisor;
import io.github.aaronsamuvel.blueprints.lifecycle.examples.IncidentTriage;
import io.github.aaronsamuvel.blueprints.lifecycle.examples.StructuredOutputExample;
import io.github.aaronsamuvel.blueprints.lifecycle.examples.VirtualThreadFanOut;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class LifecycleExamplesTest {

    @Test
    void modelJsonIsBoundToRecordContract() {
        StubChatModel model = StubChatModel.fixed(
                "{\"severity\":\"CRITICAL\",\"service\":\"payments\",\"summary\":\"down\",\"nextSteps\":[\"page on-call\"]}");
        IncidentTriage t = new StructuredOutputExample(ChatClient.create(model)).triage("payments 500s");
        assertThat(t.severity()).isEqualTo(IncidentTriage.Severity.CRITICAL);
        assertThat(t.nextSteps()).containsExactly("page on-call");
        // the format instructions for the record schema were appended to the prompt
        assertThat(model.allSentText()).contains("severity");
    }

    @Test
    void recordRejectsInvalidModelOutput() {
        assertThatThrownBy(() -> new IncidentTriage(null, "x", "y", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advisorSeesEveryCall() {
        AuditTrailAdvisor audit = new AuditTrailAdvisor();
        ChatClient client = ChatClient.builder(StubChatModel.echo()).defaultAdvisors(audit).build();
        client.prompt().user("one").call().content();
        client.prompt().user("two").call().content();
        assertThat(audit.entries()).hasSize(2);
        assertThat(audit.entries().get(0).responseChars()).isEqualTo("echo: one".length());
    }

    @Test
    void virtualThreadFanOutRunsOnVirtualThreads() {
        List<Boolean> virt = VirtualThreadFanOut.mapConcurrently(
                IntStream.range(0, 100).boxed().toList(), i -> Thread.currentThread().isVirtual());
        assertThat(virt).hasSize(100).containsOnly(true);
    }
}
