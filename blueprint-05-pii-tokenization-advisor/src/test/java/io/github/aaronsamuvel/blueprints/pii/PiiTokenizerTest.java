package io.github.aaronsamuvel.blueprints.pii;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aaronsamuvel.blueprints.pii.core.PiiTokenizer;
import io.github.aaronsamuvel.blueprints.pii.core.TokenVault;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class PiiTokenizerTest {

    private final PiiTokenizer tokenizer = new PiiTokenizer();

    @ParameterizedTest
    @ValueSource(strings = {
            "priya.sharma@example.com", "+91 98765 43210", "9123456789", "ABCDE1234F",
            "2345 6789 0123", "4111 1111 1111 1111"})
    void detectsAndTokenizesEachPiiType(String pii) {
        TokenVault vault = new TokenVault();
        String out = tokenizer.tokenize("value: " + pii + " end", vault);
        assertThat(out).doesNotContain(pii).contains("<<");
        assertThat(tokenizer.detokenize(out, vault)).isEqualTo("value: " + pii + " end");
    }

    @Test
    void sameValueGetsSameTokenAndRoundTrips() {
        TokenVault vault = new TokenVault();
        String text = "Mail a@b.com and again a@b.com, or c@d.org";
        String out = tokenizer.tokenize(text, vault);
        assertThat(out).isEqualTo("Mail <<EMAIL_1>> and again <<EMAIL_1>>, or <<EMAIL_2>>");
        assertThat(tokenizer.detokenize(out, vault)).isEqualTo(text);
    }

    @Test
    void nonLuhnDigitRunsAndOrdinaryTextAreLeftAlone() {
        String text = "Order 1234567890123 shipped on 2026-10-17 for 499 rupees";
        assertThat(tokenizer.tokenize(text, new TokenVault())).isEqualTo(text);
        assertThat(tokenizer.containsPii(text)).isFalse();
    }

    @Test
    void unknownTokensAreNotInvented() {
        assertThat(tokenizer.detokenize("hi <<EMAIL_9>>", new TokenVault())).isEqualTo("hi <<EMAIL_9>>");
    }

    @Test
    void redactIsIrreversible() {
        assertThat(tokenizer.redact("PAN ABCDE1234F")).isEqualTo("PAN [REDACTED_PAN]");
    }
}
