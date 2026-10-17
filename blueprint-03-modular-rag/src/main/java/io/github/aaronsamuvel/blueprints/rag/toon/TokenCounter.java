package io.github.aaronsamuvel.blueprints.rag.toon;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/** Real BPE token counts via JTokkit (the same tokenizers OpenAI models use). */
public final class TokenCounter {

    private final Encoding encoding;
    private final String name;

    public TokenCounter(EncodingType type) {
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(type);
        this.name = type.getName();
    }

    public int count(String text) {
        return encoding.countTokens(text);
    }

    public String name() {
        return name;
    }
}
