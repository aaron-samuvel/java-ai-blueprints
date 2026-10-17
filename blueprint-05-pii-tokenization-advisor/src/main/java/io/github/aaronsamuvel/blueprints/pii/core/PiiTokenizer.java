package io.github.aaronsamuvel.blueprints.pii.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces PII with vault tokens (tokenize), reverses it (detokenize), or masks it (redact). */
public final class PiiTokenizer {

    private static final Pattern TOKEN = Pattern.compile("<<[A-Z]+_\\d+>>");

    public String tokenize(String text, TokenVault vault) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        for (PiiType type : PiiType.values()) {
            Matcher m = type.pattern().matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String value = m.group();
                String replacement = (type == PiiType.CARD && !luhn(value)) ? value : vault.tokenFor(type, value);
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    public String detokenize(String text, TokenVault vault) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String raw = vault.valueFor(m.group());
            m.appendReplacement(sb, Matcher.quoteReplacement(raw != null ? raw : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Irreversible masking, e.g. for logs or tool output marked REDACT. */
    public String redact(String text) {
        if (text == null) return null;
        String out = text;
        for (PiiType type : PiiType.values()) {
            Matcher m = type.pattern().matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String r = (type == PiiType.CARD && !luhn(m.group())) ? m.group() : "[REDACTED_" + type.name() + "]";
                m.appendReplacement(sb, Matcher.quoteReplacement(r));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    public boolean containsPii(String text) {
        return !redact(text).equals(text);
    }

    static boolean luhn(String number) {
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.length() < 13 || digits.length() > 19) return false;
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }
}
