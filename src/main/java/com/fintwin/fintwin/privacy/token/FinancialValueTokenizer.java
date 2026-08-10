package com.fintwin.fintwin.privacy.token;

import com.fintwin.fintwin.privacy.domain.FinancialReference;
import com.fintwin.fintwin.privacy.domain.ReferenceType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FinancialValueTokenizer {
    private static final Pattern ABSOLUTE_DATE = Pattern.compile(
            "(?<!\\d)(?:\\d{4}\\s*년\\s*(?:1[0-2]|0?[1-9])\\s*월|\\d{4}-(?:1[0-2]|0[1-9]))");
    private static final Pattern MONEY = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:\\d{1,3}(?:,\\d{3})+"
                    + "|\\d+(?:\\.\\d+)?\\s*억(?:\\s*\\d+(?:\\s*(?:천|백|십))?\\s*만)?"
                    + "|\\d+(?:\\s*(?:천|백|십))?\\s*만|\\d+)\\s*원(?!\\d)");
    private static final Pattern PERCENT = Pattern.compile("(?<![\\d.])\\d+(?:\\.\\d+)?\\s*%(?!\\d)");
    private static final Pattern DURATION = Pattern.compile(
            "(?<!\\d)\\d+\\s*(?:개월|년)(?!\\s*(?:뒤|후|전))(?!\\d)");
    private static final Pattern KOREAN_DATE_PARTS = Pattern.compile(
            "(?<year>\\d{4})\\s*년\\s*(?<month>1[0-2]|0?[1-9])\\s*월");

    private final KoreanMoneyParser moneyParser;

    public FinancialValueTokenizer(KoreanMoneyParser moneyParser) {
        this.moneyParser = Objects.requireNonNull(moneyParser);
    }

    public FinancialTokenizationResult tokenize(String source) {
        Objects.requireNonNull(source);
        List<Candidate> candidates = new ArrayList<>();
        addCandidates(candidates, source, ABSOLUTE_DATE, ReferenceType.DATE, 2, this::parseDate);
        addCandidates(candidates, source, MONEY, ReferenceType.MONEY, 3, moneyParser::parse);
        addCandidates(candidates, source, PERCENT, ReferenceType.PERCENT, 4, this::parsePercent);
        addCandidates(candidates, source, DURATION, ReferenceType.DURATION, 5, this::parseDurationMonths);

        List<Candidate> selected = selectNonOverlapping(candidates, source.length());
        selected.sort(Comparator.comparingInt(candidate -> candidate.start));
        Map<ReferenceType, Integer> counters = new EnumMap<>(ReferenceType.class);
        FinancialReferenceVault.Builder vault = FinancialReferenceVault.builder();
        List<FinancialReference> references = new ArrayList<>();
        StringBuilder sanitized = new StringBuilder(source.length());
        int cursor = 0;
        for (Candidate candidate : selected) {
            sanitized.append(source, cursor, candidate.start);
            int sequence = counters.merge(candidate.type, 1, Integer::sum);
            String referenceId = candidate.type.name() + "_" + sequence;
            sanitized.append('[').append(referenceId).append(']');
            vault.put(referenceId, candidate.type, candidate.value);
            references.add(new FinancialReference(referenceId, candidate.type));
            cursor = candidate.end;
        }
        sanitized.append(source, cursor, source.length());
        return new FinancialTokenizationResult(sanitized.toString(), references, vault.build());
    }

    private void addCandidates(List<Candidate> candidates, String source, Pattern pattern,
                               ReferenceType type, int priority, Function<String, Object> parser) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            candidates.add(new Candidate(matcher.start(), matcher.end(), type, priority,
                    parser.apply(matcher.group())));
        }
    }

    private List<Candidate> selectNonOverlapping(List<Candidate> candidates, int sourceLength) {
        candidates.sort(Comparator.comparingInt((Candidate candidate) -> candidate.priority)
                .thenComparingInt(candidate -> -(candidate.end - candidate.start))
                .thenComparingInt(candidate -> candidate.start));
        boolean[] occupied = new boolean[sourceLength];
        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean overlaps = false;
            for (int index = candidate.start; index < candidate.end; index++) {
                if (occupied[index]) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                selected.add(candidate);
                for (int index = candidate.start; index < candidate.end; index++) {
                    occupied[index] = true;
                }
            }
        }
        return selected;
    }

    private YearMonth parseDate(String expression) {
        String normalized = expression.strip();
        if (normalized.contains("-")) {
            return YearMonth.parse(normalized);
        }
        Matcher matcher = KOREAN_DATE_PARTS.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Date expression is invalid");
        }
        return YearMonth.of(Integer.parseInt(matcher.group("year")), Integer.parseInt(matcher.group("month")));
    }

    private BigDecimal parsePercent(String expression) {
        return new BigDecimal(expression.replace("%", "").strip())
                .setScale(4, RoundingMode.HALF_UP);
    }

    private Integer parseDurationMonths(String expression) {
        String normalized = expression.replaceAll("\\s+", "");
        if (normalized.endsWith("개월")) {
            return Integer.parseInt(normalized.substring(0, normalized.length() - 2));
        }
        int years = Integer.parseInt(normalized.substring(0, normalized.length() - 1));
        return Math.multiplyExact(years, 12);
    }

    private static final class Candidate {
        private final int start;
        private final int end;
        private final ReferenceType type;
        private final int priority;
        private final Object value;

        private Candidate(int start, int end, ReferenceType type, int priority, Object value) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.priority = priority;
            this.value = value;
        }
    }
}
