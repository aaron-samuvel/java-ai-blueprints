package io.github.aaronsamuvel.blueprints.goap.ooda;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/** Reads {@code day,service,cost_inr,cpu} CSV files (header line skipped). */
public final class BillingCsv {
    private BillingCsv() {}

    public static List<CostSample> parse(String csv) {
        return csv.lines().skip(1).filter(l -> !l.isBlank()).map(l -> {
            String[] p = l.split(",");
            return new CostSample(LocalDate.parse(p[0].strip()), p[1].strip(), new BigDecimal(p[2].strip()), Double.parseDouble(p[3].strip()));
        }).toList();
    }

    public static List<CostSample> classpath(String path) {
        try {
            return parse(new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
