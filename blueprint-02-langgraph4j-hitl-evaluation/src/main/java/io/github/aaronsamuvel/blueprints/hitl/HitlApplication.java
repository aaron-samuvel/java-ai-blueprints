package io.github.aaronsamuvel.blueprints.hitl;

import io.github.aaronsamuvel.blueprints.eval.EvaluationGate;
import io.github.aaronsamuvel.blueprints.eval.LexicalJudgeChatModel;
import io.github.aaronsamuvel.blueprints.shared.StubModelConfiguration;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Offline defaults: stub drafter (echo), lexical judge, in-memory checkpoints. */
@SpringBootApplication
@Import(StubModelConfiguration.class)
public class HitlApplication {

    public static void main(String[] args) {
        SpringApplication.run(HitlApplication.class, args);
    }

    @Bean
    ReviewWorkflow reviewWorkflow(ChatModel chatModel,
                                  @Value("${blueprints.hitl.max-revisions:2}") int maxRevisions) throws GraphStateException {
        EvaluationGate gate = new EvaluationGate(new LexicalJudgeChatModel());
        return new ReviewWorkflow(ReviewGraph.build(chatModel, gate, new MemorySaver(), maxRevisions));
    }
}
