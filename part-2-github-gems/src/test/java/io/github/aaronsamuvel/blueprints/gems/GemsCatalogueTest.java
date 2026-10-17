package io.github.aaronsamuvel.blueprints.gems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class GemsCatalogueTest {

    /** Surefire runs with the module folder as working directory, so the repo root is one level up. */
    static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    final GemsCatalogue catalogue = GemsCatalogue.load();

    @Test
    void coversTheTenHandbookEntries() {
        assertThat(catalogue.handbookEntries()).isEqualTo(10);
        assertThat(catalogue.gems()).hasSize(15); // rows 1, 2, 4, 9 and 10 name two repositories each
    }

    @Test
    void everyRelatedFolderExistsInThisRepo() {
        for (Gem g : catalogue.gems()) {
            g.related().ifPresent(folder ->
                    assertThat(REPO_ROOT.resolve(folder)).as(g.slug() + " -> " + folder).isDirectory());
        }
    }

    @Test
    void readmeAndCatalogueListTheSameRepositories() throws Exception {
        String readme = Files.readString(REPO_ROOT.resolve("part-2-github-gems/README.md"));
        Matcher m = Pattern.compile("\\[([A-Za-z0-9-]+/[A-Za-z0-9._-]+)]\\(https://github\\.com/").matcher(readme);
        Set<String> inReadme = new java.util.HashSet<>();
        while (m.find()) inReadme.add(m.group(1).toLowerCase());
        Set<String> inCsv = catalogue.gems().stream().map(g -> g.slug().toLowerCase()).collect(Collectors.toSet());
        assertThat(inReadme).isEqualTo(inCsv);
    }

    @Test
    void reuseAdviceFollowsTheLicence() {
        assertThat(ReuseAdvice.forLicence("MIT")).isEqualTo(ReuseAdvice.PERMISSIVE_WITH_ATTRIBUTION);
        assertThat(ReuseAdvice.forLicence("Apache-2.0")).isEqualTo(ReuseAdvice.PERMISSIVE_WITH_ATTRIBUTION);
        assertThat(ReuseAdvice.forLicence("GPL-3.0")).isEqualTo(ReuseAdvice.COPYLEFT_CHECK_FIRST);
        assertThat(ReuseAdvice.forLicence("none")).isEqualTo(ReuseAdvice.READ_ONLY);
        assertThat(ReuseAdvice.forLicence("NOASSERTION")).isEqualTo(ReuseAdvice.READ_ONLY);
        assertThat(ReuseAdvice.forLicence("Some-Custom-1.0")).isEqualTo(ReuseAdvice.READ_ONLY);
        assertThat(catalogue.gems().stream().filter(g -> g.reuseAdvice() == ReuseAdvice.READ_ONLY).map(Gem::slug))
                .containsExactlyInAnyOrder("iunera/ypipe", "rajeshkawali/Springboot-ai-playground",
                        "ykpraveen/langchain4j-sample", "anmiralles/springboot-ai-rag-docingest");
    }

    @Test
    void rejectsMalformedRowsAndDuplicates() {
        assertThatThrownBy(() -> GemsCatalogue.parse(List.of("1,not a slug,MIT,Java,-")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GemsCatalogue.parse(List.of("11,a/b,MIT,Java,-")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GemsCatalogue.parse(List.of("1,a/b,MIT,Java")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GemsCatalogue.parse(List.of("1,a/b,MIT,Java,-", "2,A/B,MIT,Java,-")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markdownTableHasOneRowPerRepositoryAndFindsRelatedGems() {
        String md = catalogue.toMarkdown();
        assertThat(md.lines().filter(l -> l.startsWith("| ") && l.contains("https://github.com/"))).hasSize(15);
        assertThat(md).contains("| 8 | [iunera/ypipe](https://github.com/iunera/ypipe) | none | no licence detected");
        assertThat(catalogue.relatedTo("blueprint-02-langgraph4j-hitl-evaluation")).extracting(Gem::slug)
                .containsExactly("langgraph4j/langgraph4j", "ykpraveen/langchain4j-sample");
        assertThat(catalogue.relatedTo("blueprint-07-a2a-nl2sql-agent")).extracting(Gem::slug)
                .containsExactly("alibaba/spring-ai-alibaba", "rainerWJY/JManus");
    }
}
