package io.github.aaronsamuvel.blueprints.gems;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Loads the Part II catalogue from {@code gems.csv} on the classpath. */
public final class GemsCatalogue {

    private final List<Gem> gems;

    private GemsCatalogue(List<Gem> gems) {
        this.gems = List.copyOf(gems);
    }

    public static GemsCatalogue load() {
        try (InputStream in = GemsCatalogue.class.getResourceAsStream("/gems.csv")) {
            if (in == null) throw new IllegalStateException("gems.csv not on the classpath");
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses CSV lines; blank lines and lines starting with {@code #} are ignored. */
    public static GemsCatalogue parse(List<String> lines) {
        List<Gem> out = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split(",", -1);
            if (f.length != 5) throw new IllegalArgumentException("expected 5 columns: " + line);
            String related = f[4].strip();
            out.add(new Gem(f[0].strip(), f[1].strip(), f[2].strip(), f[3].strip(),
                    related.equals("-") || related.isEmpty() ? Optional.empty() : Optional.of(related)));
        }
        long distinct = out.stream().map(g -> g.slug().toLowerCase()).distinct().count();
        if (distinct != out.size()) throw new IllegalArgumentException("duplicate repository in catalogue");
        return new GemsCatalogue(out);
    }

    public List<Gem> gems() {
        return gems;
    }

    /** Number of distinct handbook entries (1a and 1b, 4a and 4b, ... count once). */
    public int handbookEntries() {
        return (int) gems.stream().map(Gem::handbookEntry).distinct().count();
    }

    public List<Gem> relatedTo(String folder) {
        return gems.stream().filter(g -> g.related().filter(folder::equals).isPresent()).toList();
    }

    /** Markdown table: number, repository, licence, what you may reuse, related folder. */
    public String toMarkdown() {
        String rows = gems.stream().map(g -> "| %s | [%s](%s) | %s | %s | %s |".formatted(
                g.number(), g.slug(), g.url(), g.licence(), g.reuseAdvice().text(),
                g.related().map(r -> "`" + r + "`").orElse("—"))).collect(Collectors.joining("\n"));
        return "| # | Repository | Licence | Reuse | Compare with |\n|---|---|---|---|---|\n" + rows + "\n";
    }
}
