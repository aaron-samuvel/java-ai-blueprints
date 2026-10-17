package io.github.aaronsamuvel.blueprints.lifecycle;

import io.github.aaronsamuvel.blueprints.lifecycle.examples.AuditTrailAdvisor;
import io.github.aaronsamuvel.blueprints.lifecycle.examples.StructuredOutputExample;
import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.ai.chat.client.ChatClient;

/** Offline demo: run with {@code mvn -pl part-1-lifecycle -am spring-boot:run}, {@code java -jar} on the packaged jar, or from your IDE. */
public class LifecycleDemoApplication {

    public static void main(String[] args) {
        StubChatModel model = StubChatModel.fixed("""
                {"severity":"HIGH","service":"checkout","summary":"p95 latency over 2s","nextSteps":["scale pods","check DB"]}""");
        AuditTrailAdvisor audit = new AuditTrailAdvisor();
        ChatClient client = ChatClient.builder(model).defaultAdvisors(audit).build();
        System.out.println(new StructuredOutputExample(client).triage("checkout p95 latency 2.4s"));
        System.out.println(audit.entries());
    }
}
