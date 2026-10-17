package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

/** One detected entity: character range {@code [start, end)} of the analysed text. */
public record PiiSpan(PiiEntityType type, int start, int end, String text, double score, String analyzer) {

    public PiiSpan {
        if (start < 0 || end <= start) throw new IllegalArgumentException("bad span " + start + ".." + end);
    }

    public int length() {
        return end - start;
    }

    public boolean overlaps(PiiSpan o) {
        return start < o.end && o.start < end;
    }
}
