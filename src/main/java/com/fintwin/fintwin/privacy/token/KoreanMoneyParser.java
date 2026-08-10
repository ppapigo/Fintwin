package com.fintwin.fintwin.privacy.token;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KoreanMoneyParser {
    private static final BigDecimal EOK = new BigDecimal("100000000");
    private static final BigDecimal MAN = new BigDecimal("10000");
    private static final Pattern SMALL_UNIT = Pattern.compile("(\\d*(?:\\.\\d+)?)(천|백|십)");

    public BigDecimal parse(String expression) {
        if (expression == null) {
            throw new IllegalArgumentException("Money expression is required");
        }
        String normalized = expression.replaceAll("\\s+", "")
                .replace(",", "")
                .replace("원", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Money expression is invalid");
        }

        BigDecimal total = BigDecimal.ZERO;
        int eokIndex = normalized.indexOf('억');
        if (eokIndex >= 0) {
            total = total.add(number(normalized.substring(0, eokIndex)).multiply(EOK));
            normalized = normalized.substring(eokIndex + 1);
        }
        int manIndex = normalized.indexOf('만');
        if (manIndex >= 0) {
            total = total.add(parseSmall(normalized.substring(0, manIndex)).multiply(MAN));
            normalized = normalized.substring(manIndex + 1);
        }
        if (!normalized.isEmpty()) {
            total = total.add(parseSmall(normalized));
        }
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("Money expression must be positive");
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseSmall(String value) {
        if (value.isEmpty()) {
            return BigDecimal.ONE;
        }
        if (value.matches("\\d+(?:\\.\\d+)?")) {
            return number(value);
        }
        Matcher matcher = SMALL_UNIT.matcher(value);
        BigDecimal total = BigDecimal.ZERO;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException("Money expression is invalid");
            }
            BigDecimal coefficient = matcher.group(1).isEmpty() ? BigDecimal.ONE : number(matcher.group(1));
            BigDecimal unit = switch (matcher.group(2)) {
                case "천" -> new BigDecimal("1000");
                case "백" -> new BigDecimal("100");
                case "십" -> new BigDecimal("10");
                default -> throw new IllegalArgumentException("Money expression is invalid");
            };
            total = total.add(coefficient.multiply(unit));
            end = matcher.end();
        }
        if (end < value.length()) {
            String remainder = value.substring(end);
            if (!remainder.matches("\\d+(?:\\.\\d+)?")) {
                throw new IllegalArgumentException("Money expression is invalid");
            }
            total = total.add(number(remainder));
        }
        return total;
    }

    private BigDecimal number(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Money expression is invalid");
        }
    }
}
