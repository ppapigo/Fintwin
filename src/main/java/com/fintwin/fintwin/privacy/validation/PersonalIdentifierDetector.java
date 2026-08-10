package com.fintwin.fintwin.privacy.validation;

import com.fintwin.fintwin.privacy.domain.PersonalIdentifierType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PersonalIdentifierDetector {
    private static final Pattern RESIDENT_REGISTRATION_NUMBER = Pattern.compile(
            "(?<!\\d)\\d{6}-?[1-4]\\d{6}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9._%+-])");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?82[- ]?)?0?(?:10|11|16|17|18|19|2|[3-6]\\d)[- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)");
    private static final Pattern CARD = Pattern.compile(
            "(?<!\\d)(?:\\d{13,19}|\\d{4}(?:[- ]\\d{4}){3})(?!\\d)");
    private static final Pattern ACCOUNT = Pattern.compile("(?<!\\d)\\d{2,6}(?:-\\d{2,6}){2,4}(?!\\d)");
    private static final Pattern API_KEY_OR_SECRET = Pattern.compile(
            "(?i)(?:sk-[A-Z0-9_-]{16,}|AIza[A-Z0-9_-]{20,}|AKIA[A-Z0-9]{16}"
                    + "|(?:api[_ -]?key|secret|access[_ -]?token)\\s*[:=]\\s*[A-Z0-9_./+=-]{8,}"
                    + "|Bearer\\s+[A-Z0-9._-]{16,})");
    private static final Pattern LONG_NUMERIC_SEQUENCE = Pattern.compile("(?<!\\d)\\d{20,}(?!\\d)");

    public List<PersonalIdentifierType> detect(String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        add(candidates, source, RESIDENT_REGISTRATION_NUMBER,
                PersonalIdentifierType.RESIDENT_REGISTRATION_NUMBER, 1);
        add(candidates, source, EMAIL, PersonalIdentifierType.EMAIL, 2);
        add(candidates, source, PHONE, PersonalIdentifierType.PHONE_NUMBER, 3);
        add(candidates, source, API_KEY_OR_SECRET, PersonalIdentifierType.API_KEY_OR_SECRET, 4);
        addCardCandidates(candidates, source);
        add(candidates, source, ACCOUNT, PersonalIdentifierType.ACCOUNT_NUMBER, 6);
        add(candidates, source, LONG_NUMERIC_SEQUENCE, PersonalIdentifierType.LONG_NUMERIC_SEQUENCE, 7);
        source.codePoints().forEachOrdered(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                candidates.add(new Candidate(0, 0, PersonalIdentifierType.CONTROL_CHARACTER, 0));
            }
        });

        candidates.sort(Comparator.comparingInt((Candidate candidate) -> candidate.priority)
                .thenComparingInt(candidate -> candidate.start));
        boolean[] occupied = new boolean[source.length()];
        Set<PersonalIdentifierType> detected = EnumSet.noneOf(PersonalIdentifierType.class);
        for (Candidate candidate : candidates) {
            if (candidate.type == PersonalIdentifierType.CONTROL_CHARACTER) {
                detected.add(candidate.type);
                continue;
            }
            boolean overlaps = false;
            for (int index = candidate.start; index < candidate.end; index++) {
                if (occupied[index]) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                detected.add(candidate.type);
                for (int index = candidate.start; index < candidate.end; index++) {
                    occupied[index] = true;
                }
            }
        }
        return List.copyOf(detected);
    }

    private void add(List<Candidate> candidates, String source, Pattern pattern,
                     PersonalIdentifierType type, int priority) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            candidates.add(new Candidate(matcher.start(), matcher.end(), type, priority));
        }
    }

    private void addCardCandidates(List<Candidate> candidates, String source) {
        Matcher matcher = CARD.matcher(source);
        while (matcher.find()) {
            long digitCount = matcher.group().chars().filter(Character::isDigit).count();
            if (digitCount >= 13 && digitCount <= 19) {
                candidates.add(new Candidate(matcher.start(), matcher.end(),
                        PersonalIdentifierType.CARD_NUMBER, 5));
            }
        }
    }

    private static final class Candidate {
        private final int start;
        private final int end;
        private final PersonalIdentifierType type;
        private final int priority;

        private Candidate(int start, int end, PersonalIdentifierType type, int priority) {
            this.start = start;
            this.end = end;
            this.type = type;
            this.priority = priority;
        }
    }
}
