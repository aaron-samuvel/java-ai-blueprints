package io.github.aaronsamuvel.blueprints.gems;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Live check (needs internet, runs only with {@code -Pit}): every catalogued repository still exists on GitHub
 * and still reports the licence recorded in {@code gems.csv}. Uses the public API without a token
 * (60 requests per hour per IP is enough for 11 repositories).
 */
class GitHubCatalogueIT {

    static final Pattern SPDX = Pattern.compile("\"spdx_id\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void everyRepositoryExistsAndKeepsItsLicence() throws Exception {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build();
        List<String> drift = new ArrayList<>();
        for (Gem g : GemsCatalogue.load().gems()) {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + g.slug()))
                    .header("Accept", "application/vnd.github+json").timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).as(g.slug()).isEqualTo(200);
            Matcher m = SPDX.matcher(r.body());
            String live = m.find() ? m.group(1) : "none";
            if (live.equals("NOASSERTION")) live = "none";
            if (!live.equalsIgnoreCase(g.licence())) drift.add(g.slug() + ": catalogue " + g.licence() + ", GitHub " + live);
        }
        assertThat(drift).as("licence drift; update gems.csv and the README").isEmpty();
    }
}
