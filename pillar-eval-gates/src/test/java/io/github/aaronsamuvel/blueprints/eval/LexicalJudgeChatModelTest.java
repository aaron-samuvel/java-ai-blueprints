package io.github.aaronsamuvel.blueprints.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LexicalJudgeChatModelTest {

    @Test
    void supportIsFractionOfContentWordsFound() {
        assertThat(LexicalJudgeChatModel.support("full refund within 30 days", "request a full refund within 30 days")).isEqualTo(1.0);
        assertThat(LexicalJudgeChatModel.support("refund within 90 days", "refund within 30 days")).isLessThan(1.0);
        assertThat(LexicalJudgeChatModel.support("", "anything")).isEqualTo(1.0);
    }

    @Test
    void numbersAreContentWords() {
        assertThat(LexicalJudgeChatModel.words("within 7 days")).contains("7");
    }
}
