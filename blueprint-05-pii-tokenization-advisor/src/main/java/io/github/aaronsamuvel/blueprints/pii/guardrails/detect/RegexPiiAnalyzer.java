package io.github.aaronsamuvel.blueprints.pii.guardrails.detect;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-precision regex recognizers: email, phone, credit card (Luhn), US SSN, Indian PAN and
 * Aadhaar, IBAN (ISO 13616 mod-97 checksum) and bank account numbers (only after an
 * "account"/"a/c" keyword, to avoid flagging every long number).
 */
public final class RegexPiiAnalyzer implements PiiAnalyzerSpi {

    private record Recognizer(PiiEntityType type, Pattern pattern, int group, Predicate<String> validator, double score) {}

    private static final List<Recognizer> RECOGNIZERS = List.of(
            new Recognizer(PiiEntityType.EMAIL, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), 0, s -> true, 0.99),
            new Recognizer(PiiEntityType.IBAN, Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,4})?\\b"), 0, RegexPiiAnalyzer::ibanValid, 0.97),
            new Recognizer(PiiEntityType.SSN, Pattern.compile("\\b(?!000|666|9\\d\\d)\\d{3}-(?!00)\\d{2}-(?!0000)\\d{4}\\b"), 0, s -> true, 0.9),
            new Recognizer(PiiEntityType.PAN, Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b"), 0, s -> true, 0.9),
            new Recognizer(PiiEntityType.CREDIT_CARD, Pattern.compile("\\b(?:\\d[ -]?){12,18}\\d\\b"), 0, RegexPiiAnalyzer::luhn, 0.95),
            new Recognizer(PiiEntityType.ACCOUNT_NUMBER, Pattern.compile("(?i)\\b(?:account|acct|a/c)(?:\\s*(?:no\\.?|number|#))?\\s*[:#]?\\s*(\\d{9,18})\\b"), 1, s -> true, 0.85),
            new Recognizer(PiiEntityType.AADHAAR, Pattern.compile("\\b[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}\\b"), 0, s -> true, 0.8),
            new Recognizer(PiiEntityType.PHONE, Pattern.compile("(?<![\\w+])(?:\\+91[ -]?[6-9]\\d{4}[ -]?\\d{5}|[6-9]\\d{4}[ -]?\\d{5}|\\+1[ -]?\\(?\\d{3}\\)?[ -]?\\d{3}[ -]?\\d{4})\\b"), 0, s -> true, 0.8));

    @Override
    public String name() {
        return "regex";
    }

    @Override
    public List<PiiSpan> analyze(String text) {
        List<PiiSpan> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (Recognizer r : RECOGNIZERS) {
            Matcher m = r.pattern().matcher(text);
            while (m.find()) {
                String value = m.group(r.group());
                if (r.validator().test(value)) {
                    out.add(new PiiSpan(r.type(), m.start(r.group()), m.end(r.group()), value, r.score(), name()));
                }
            }
        }
        return out;
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

    static boolean ibanValid(String iban) {
        String s = iban.replace(" ", "").toUpperCase(Locale.ROOT);
        if (s.length() < 15 || s.length() > 34) return false;
        String rearranged = s.substring(4) + s.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
