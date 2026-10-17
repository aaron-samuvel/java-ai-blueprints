package io.github.aaronsamuvel.blueprints.hitl.submission;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;

final class Samples {
    private Samples() {}

    static String load(String name) {
        try {
            return new ClassPathResource("submissions/" + name + ".md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
