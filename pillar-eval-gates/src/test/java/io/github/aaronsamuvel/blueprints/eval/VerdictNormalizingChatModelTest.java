package io.github.aaronsamuvel.blueprints.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerdictNormalizingChatModelTest {

    @Test
    void reducesVerboseRepliesToBareVerdicts() {
        assertThat(VerdictNormalizingChatModel.normalize("Yes.")).isEqualTo("YES");
        assertThat(VerdictNormalizingChatModel.normalize("  **YES**, the claim is supported")).isEqualTo("YES");
        assertThat(VerdictNormalizingChatModel.normalize("No.")).isEqualTo("NO");
        assertThat(VerdictNormalizingChatModel.normalize("The answer is unclear")).isEqualTo("NO");
        assertThat(VerdictNormalizingChatModel.normalize(null)).isEqualTo("NO");
    }
}
