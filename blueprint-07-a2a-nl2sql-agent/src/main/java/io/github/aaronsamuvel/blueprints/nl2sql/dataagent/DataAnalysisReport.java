package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.util.List;
import java.util.Map;

/** Handbook Part III, Blueprint 7: the typed result of the DataAgent. */
public record DataAnalysisReport(String naturalLanguageSummary, String executedSql, List<Map<String, Object>> rows,
                                 ChartSpecification suggestedChart) {}
