package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

/** Suggested visualisation of a result set. */
public record ChartSpecification(Type type, String xField, String yField, String title) {
    public enum Type { BAR, LINE, TABLE }
}
