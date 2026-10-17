package io.github.aaronsamuvel.blueprints.shared.env;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.aaronsamuvel.blueprints.shared.env.EnvironmentProbe.ProbeResult;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Actuator health for the AI supporting environment, served at {@code /actuator/health/aiEnvironment}
 * (the bean is named {@code aiEnvironmentHealthIndicator}). DOWN if any configured probe is DOWN;
 * probes reporting NOT_CONFIGURED are listed but do not change the status.
 */
public class SupportingEnvironmentHealthIndicator implements HealthIndicator {

    private final List<EnvironmentProbe> probes;

    public SupportingEnvironmentHealthIndicator(List<EnvironmentProbe> probes) {
        this.probes = List.copyOf(probes);
    }

    public List<ProbeResult> runProbes() {
        return probes.stream().map(EnvironmentProbe::probe).toList();
    }

    @Override
    public Health health() {
        List<ProbeResult> results = runProbes();
        boolean down = results.stream().anyMatch(r -> r.state() == ProbeResult.State.DOWN);
        Map<String, Object> details = results.stream().collect(Collectors.toMap(ProbeResult::component,
                r -> r.state() + ": " + r.detail(), (a, b) -> a + "; " + b, java.util.LinkedHashMap::new));
        return (down ? Health.down() : Health.up()).withDetails(details).build();
    }

    /** Fixed-width table for the startup log. */
    public static String table(List<ProbeResult> results) {
        int w = Math.max(9, results.stream().mapToInt(r -> r.component().length()).max().orElse(9));
        String fmt = "%-" + w + "s  %-14s  %s%n";
        StringBuilder sb = new StringBuilder(String.format(fmt, "COMPONENT", "STATE", "DETAIL"));
        results.forEach(r -> sb.append(String.format(fmt, r.component(), r.state(), r.detail())));
        return sb.toString();
    }
}
