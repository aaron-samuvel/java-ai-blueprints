package io.github.aaronsamuvel.blueprints.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/**
 * Live judge: Ollama llama3.2:3b. A 3B judge is noisy, so the live gate is looser (60%) than the
 * deterministic offline gate (90%), and each regression is only required to fail one of the two
 * evaluators. Run: mvn -B -Pit verify -pl pillar-eval-gates -am
 */
class OllamaJudgeIT {

    @Test
    void liveJudgeAgreesWithGoldenSetAndCatchesRegressions() {
        String base = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("OLLAMA_CHAT_MODEL", "llama3.2:3b");
        OllamaChatModel judge = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(base).build())
                .options(OllamaChatOptions.builder().model(model).temperature(0.0).build())
                .build();
        EvaluationGate gate = new EvaluationGate(new VerdictNormalizingChatModel(judge));

        GateReport golden = gate.run(EvaluationGate.load("golden-set.json"));
        System.out.println("live golden: " + golden.summary());
        assertThat(golden.passes(0.6, 0.6)).as(golden.summary()).isTrue();

        GateReport bad = gate.run(EvaluationGate.load("regression-set.json"));
        System.out.println("live regressions: " + bad.summary());
        assertThat(bad.failures()).hasSizeGreaterThanOrEqualTo(2);
    }
}
