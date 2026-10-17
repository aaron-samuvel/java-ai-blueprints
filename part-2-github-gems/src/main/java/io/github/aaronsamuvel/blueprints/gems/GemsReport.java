package io.github.aaronsamuvel.blueprints.gems;

/**
 * Prints the catalogue as a Markdown table, with reuse advice derived from each licence.
 * Run: {@code mvn -pl part-2-github-gems -am spring-boot:run} or {@code java -jar part-2-github-gems/target/part-2-github-gems-0.1.0-SNAPSHOT.jar}.
 */
public final class GemsReport {

    private GemsReport() {}

    public static void main(String[] args) {
        GemsCatalogue catalogue = GemsCatalogue.load();
        System.out.println("Part II: " + catalogue.handbookEntries() + " handbook entries, "
                + catalogue.gems().size() + " repositories (licences as reported by GitHub on 2026-10-17)\n");
        System.out.print(catalogue.toMarkdown());
    }
}
