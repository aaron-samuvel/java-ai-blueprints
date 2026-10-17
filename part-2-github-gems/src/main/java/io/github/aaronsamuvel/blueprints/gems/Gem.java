package io.github.aaronsamuvel.blueprints.gems;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One Part II repository.
 *
 * @param number   handbook position, e.g. {@code "4a"}
 * @param slug     GitHub {@code owner/name}
 * @param licence  SPDX id reported by GitHub, or {@code "none"}
 * @param language primary language reported by GitHub
 * @param related  folder in this repo that the gem is compared with, if any
 */
public record Gem(String number, String slug, String licence, String language, Optional<String> related) {

    static final Pattern NUMBER = Pattern.compile("([1-9]|10)[ab]?");
    static final Pattern SLUG = Pattern.compile("[A-Za-z0-9-]+/[A-Za-z0-9._-]+");

    public Gem {
        Objects.requireNonNull(related, "related");
        if (number == null || !NUMBER.matcher(number).matches()) throw new IllegalArgumentException("bad number: " + number);
        if (slug == null || !SLUG.matcher(slug).matches()) throw new IllegalArgumentException("bad slug: " + slug);
        if (licence == null || licence.isBlank()) throw new IllegalArgumentException("licence missing for " + slug);
        if (language == null || language.isBlank()) throw new IllegalArgumentException("language missing for " + slug);
    }

    public String url() {
        return "https://github.com/" + slug;
    }

    /** Handbook number without the a/b suffix, so 4a and 4b both count as entry 4. */
    public int handbookEntry() {
        return Integer.parseInt(number.replaceAll("[ab]$", ""));
    }

    public ReuseAdvice reuseAdvice() {
        return ReuseAdvice.forLicence(licence);
    }
}
