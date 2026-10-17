package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import opennlp.tools.dictionary.Dictionary;
import opennlp.tools.namefind.DictionaryNameFinder;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;

/**
 * JVM-local Apache OpenNLP named-entity recognizer for PERSON and LOCATION.
 * <p>By default it uses OpenNLP's {@link DictionaryNameFinder} over the bundled gazetteers
 * ({@code privacy/person-names.txt}, {@code privacy/locations.txt}), so it works offline without a
 * model download. If statistical models are configured ({@code blueprints.privacy.opennlp.person-model},
 * {@code ...location-model}: paths to OpenNLP {@code TokenNameFinderModel} files such as
 * {@code en-ner-person.bin}), {@link NameFinderME} runs in addition to the dictionaries.
 */
public final class OpenNlpPiiAnalyzer implements PiiAnalyzerSpi {

    private final Map<PiiEntityType, List<TokenNameFinder>> finders = new LinkedHashMap<>();
    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;

    public OpenNlpPiiAnalyzer(String personModelPath, String locationModelPath) {
        add(PiiEntityType.PERSON, new DictionaryNameFinder(dictionary("privacy/person-names.txt"), "person"));
        add(PiiEntityType.LOCATION, new DictionaryNameFinder(dictionary("privacy/locations.txt"), "location"));
        if (personModelPath != null && !personModelPath.isBlank()) add(PiiEntityType.PERSON, model(personModelPath));
        if (locationModelPath != null && !locationModelPath.isBlank()) add(PiiEntityType.LOCATION, model(locationModelPath));
    }

    public OpenNlpPiiAnalyzer() {
        this(null, null);
    }

    private void add(PiiEntityType type, TokenNameFinder finder) {
        finders.computeIfAbsent(type, t -> new ArrayList<>()).add(finder);
    }

    /** Number of statistical (model-based) finders loaded, for diagnostics. */
    public long statisticalFinders() {
        return finders.values().stream().flatMap(List::stream).filter(f -> f instanceof NameFinderME).count();
    }

    @Override
    public String name() {
        return "opennlp";
    }

    @Override
    public synchronized List<PiiSpan> analyze(String text) {
        List<PiiSpan> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        Span[] pos = tokenizer.tokenizePos(text);
        String[] tokens = Span.spansToStrings(pos, text);
        finders.forEach((type, list) -> {
            for (TokenNameFinder f : list) {
                for (Span s : f.find(tokens)) {
                    int start = pos[s.getStart()].getStart();
                    int end = pos[s.getEnd() - 1].getEnd();
                    double score = f instanceof NameFinderME ? s.getProb() : 0.75;
                    out.add(new PiiSpan(type, start, end, text.substring(start, end), score, name()));
                }
                f.clearAdaptiveData();
            }
        });
        return out;
    }

    private static Dictionary dictionary(String resource) {
        try (InputStream in = OpenNlpPiiAnalyzer.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing gazetteer " + resource);
            Dictionary raw = Dictionary.parseOneEntryPerLine(new InputStreamReader(in, StandardCharsets.UTF_8));
            Dictionary tokenised = new Dictionary(false);
            for (var entry : raw) {
                String line = String.join(" ", entry).strip();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    tokenised.put(new opennlp.tools.util.StringList(SimpleTokenizer.INSTANCE.tokenize(line)));
                }
            }
            return tokenised;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static NameFinderME model(String path) {
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            return new NameFinderME(new TokenNameFinderModel(in));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot load OpenNLP model " + path, e);
        }
    }
}
