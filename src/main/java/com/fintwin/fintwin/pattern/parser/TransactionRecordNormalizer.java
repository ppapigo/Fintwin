package com.fintwin.fintwin.pattern.parser;

import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import com.fintwin.fintwin.pattern.domain.TransactionCategory;
import com.fintwin.fintwin.pattern.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class TransactionRecordNormalizer {
    static final String TRANSACTION_DATE = "transactionDate";
    static final String TYPE = "type";
    static final String AMOUNT = "amount";
    static final String CATEGORY = "category";
    static final String DESCRIPTION = "description";
    static final String TRANSACTION_ID = "transactionId";
    static final Set<String> REQUIRED_HEADERS = Set.of(
            TRANSACTION_DATE, TYPE, AMOUNT, CATEGORY, DESCRIPTION);
    static final Set<String> ALLOWED_HEADERS = Set.of(
            TRANSACTION_DATE, TYPE, AMOUNT, CATEGORY, DESCRIPTION, TRANSACTION_ID);

    private static final Set<String> DIRECT_IDENTIFIER_HEADERS = Set.of(
            "account", "accountnumber", "bankaccount", "iban", "계좌번호",
            "residentregistrationnumber", "residentnumber", "nationalid", "ssn", "주민등록번호",
            "cardnumber", "creditcardnumber", "debitcardnumber", "카드번호",
            "phone", "phonenumber", "mobile", "전화번호", "휴대전화번호",
            "email", "emailaddress", "이메일", "name", "fullname", "이름",
            "bankname", "financialinstitution", "금융기관명");
    private static final Pattern MONEY_PATTERN = Pattern.compile("^\\d{1,17}(?:\\.\\d{1,2})?$");

    private final FinancialPatternRules rules;

    TransactionRecordNormalizer(FinancialPatternRules rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    Map<String, Integer> validateHeaders(List<String> headers, String prefix, ErrorFactory errors) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            if (containsControlCharacter(header)) {
                throw errors.create(prefix + "CONTROL_CHARACTER", 1, "header",
                        "Header contains a control character");
            }
            String normalizedHeader = normalizeIdentifierHeader(header);
            if (DIRECT_IDENTIFIER_HEADERS.contains(normalizedHeader)) {
                throw errors.create(prefix + "DIRECT_IDENTIFIER_HEADER", 1, "header",
                        "Direct identifier headers are not allowed");
            }
            if (!ALLOWED_HEADERS.contains(header)) {
                throw errors.create(prefix + "UNKNOWN_HEADER", 1, "header",
                        "File contains an unknown header");
            }
            if (indexes.putIfAbsent(header, index) != null) {
                throw errors.create(prefix + "DUPLICATE_HEADER", 1, header,
                        "File contains a duplicate header");
            }
        }
        for (String required : REQUIRED_HEADERS) {
            if (!indexes.containsKey(required)) {
                throw errors.create(prefix + "REQUIRED_HEADER_MISSING", 1, required,
                        "File is missing a required header");
            }
        }
        return indexes;
    }

    NormalizedTransaction normalize(Map<String, String> rawFields, Set<String> transactionIds,
                                    int rowNumber, LocalDate today, String prefix, ErrorFactory errors) {
        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, String> field : rawFields.entrySet()) {
            String value = field.getValue() == null ? "" : field.getValue();
            if (containsControlCharacter(value)) {
                throw errors.create(prefix + "CONTROL_CHARACTER", rowNumber, field.getKey(),
                        "Field contains a control character");
            }
            fields.put(field.getKey(), value);
        }

        LocalDate date = parseDate(fields.get(TRANSACTION_DATE), rowNumber, today, prefix, errors);
        TransactionType type = parseType(fields.get(TYPE), rowNumber, prefix, errors);
        BigDecimal amount = parseAmount(fields.get(AMOUNT), rowNumber, prefix, errors);
        TransactionCategory category = parseCategory(fields.get(CATEGORY), rowNumber, prefix, errors);
        String description = fields.getOrDefault(DESCRIPTION, "").strip();
        if (description.codePointCount(0, description.length()) > 100) {
            throw errors.create(prefix + "DESCRIPTION_TOO_LONG", rowNumber, DESCRIPTION,
                    "Description must be at most 100 characters");
        }
        String transactionId = rawFields.containsKey(TRANSACTION_ID)
                ? normalizeTransactionId(fields.get(TRANSACTION_ID), transactionIds, rowNumber, prefix, errors)
                : null;
        return new NormalizedTransaction(date, type, amount, category, description, transactionId);
    }

    private LocalDate parseDate(String raw, int rowNumber, LocalDate today,
                                String prefix, ErrorFactory errors) {
        try {
            LocalDate date = LocalDate.parse(String.valueOf(raw).strip());
            if (date.isAfter(today)) {
                throw errors.create(prefix + "FUTURE_DATE", rowNumber, TRANSACTION_DATE,
                        "Future transaction dates are not allowed");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw errors.create(prefix + "INVALID_DATE", rowNumber, TRANSACTION_DATE,
                    "Transaction date must use yyyy-MM-dd format");
        }
    }

    private TransactionType parseType(String raw, int rowNumber, String prefix, ErrorFactory errors) {
        try {
            return TransactionType.valueOf(String.valueOf(raw).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw errors.create(prefix + "INVALID_TYPE", rowNumber, TYPE,
                    "Transaction type is not supported");
        }
    }

    private TransactionCategory parseCategory(String raw, int rowNumber,
                                              String prefix, ErrorFactory errors) {
        try {
            return TransactionCategory.valueOf(String.valueOf(raw).strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw errors.create(prefix + "INVALID_CATEGORY", rowNumber, CATEGORY,
                    "Transaction category is not supported");
        }
    }

    private BigDecimal parseAmount(String raw, int rowNumber, String prefix, ErrorFactory errors) {
        String value = String.valueOf(raw).strip();
        if (!MONEY_PATTERN.matcher(value).matches()) {
            try {
                if (new BigDecimal(value).signum() <= 0) {
                    throw errors.create(prefix + "NON_POSITIVE_AMOUNT", rowNumber, AMOUNT,
                            "Transaction amount must be greater than zero");
                }
            } catch (NumberFormatException ignored) {
                // The safe format error below intentionally omits the source value.
            }
            throw errors.create(prefix + "INVALID_AMOUNT", rowNumber, AMOUNT,
                    "Transaction amount must be a plain decimal with at most 17 integer and 2 fraction digits");
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            throw errors.create(prefix + "NON_POSITIVE_AMOUNT", rowNumber, AMOUNT,
                    "Transaction amount must be greater than zero");
        }
        return amount.setScale(rules.moneyScale(), RoundingMode.HALF_UP);
    }

    private String normalizeTransactionId(String raw, Set<String> transactionIds, int rowNumber,
                                          String prefix, ErrorFactory errors) {
        String transactionId = String.valueOf(raw).strip();
        if (transactionId.isEmpty()) {
            return null;
        }
        if (transactionId.codePointCount(0, transactionId.length()) > 100) {
            throw errors.create(prefix + "TRANSACTION_ID_TOO_LONG", rowNumber, TRANSACTION_ID,
                    "Transaction ID must be at most 100 characters");
        }
        if (!transactionIds.add(transactionId)) {
            throw errors.create(prefix + "DUPLICATE_TRANSACTION_ID", rowNumber, TRANSACTION_ID,
                    "Transaction ID must be unique within the file");
        }
        return transactionId;
    }

    private String normalizeIdentifierHeader(String header) {
        return header.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    @FunctionalInterface
    interface ErrorFactory {
        RuntimeException create(String code, Integer row, String column, String message);
    }
}
