package io.github.aaronsamuvel.blueprints.goap.ooda;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day of spend for one service (synthetic billing export row). */
public record CostSample(LocalDate day, String service, BigDecimal costInr, double cpuUtilisation) {}
