package com.fintwin.fintwin.pattern.parser;

import com.fintwin.fintwin.global.error.CsvValidationException;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import com.fintwin.fintwin.pattern.domain.TransactionCategory;
import com.fintwin.fintwin.pattern.domain.TransactionType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransactionCsvParser {
    private static final String TRANSACTION_DATE = "transactionDate";
    private static final String TYPE = "type";
    private static final String AMOUNT = "amount";
    private static final String CATEGORY = "category";
    private static final String DESCRIPTION = "description";
    private static final String TRANSACTION_ID = "transactionId";
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            TRANSACTION_DATE, TYPE, AMOUNT, CATEGORY, DESCRIPTION);
    private static final Set<String> ALLOWED_HEADERS = Set.of(
            TRANSACTION_DATE, TYPE, AMOUNT, CATEGORY, DESCRIPTION, TRANSACTION_ID);
    private static final Set<String> DIRECT_IDENTIFIER_HEADERS = Set.of(
            "accountnumber", "residentregistrationnumber", "cardnumber", "phonenumber", "email", "name");

    private final FinancialPatternRules rules;

    public TransactionCsvParser(FinancialPatternRules rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    public List<NormalizedTransaction> parse(InputStream input, long declaredSize, LocalDate today) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(today);
        if (declaredSize <= 0) {
            throw error("CSV_EMPTY_FILE", null, "file", "CSV file must not be empty");
        }
        if (declaredSize > rules.maximumFileBytes()) {
            throw error("CSV_FILE_TOO_LARGE", null, "file", "CSV file exceeds the 2MB limit");
        }

        try (InputStream limited = new SizeLimitedInputStream(input, rules.maximumFileBytes());
             PushbackInputStream bomAware = removeUtf8Bom(limited);
             Reader reader = strictUtf8Reader(bomAware);
             CSVParser parser = CSVFormat.RFC4180.builder()
                     .setIgnoreEmptyLines(false)
                     .get()
                     .parse(reader)) {
            return parseRecords(parser.iterator(), today);
        } catch (CsvValidationException exception) {
            throw exception;
        } catch (UncheckedIOException exception) {
            throw ioError(exception.getCause());
        } catch (CharacterCodingException exception) {
            throw error("CSV_INVALID_ENCODING", null, "file", "CSV file must be valid UTF-8");
        } catch (SizeLimitIOException exception) {
            throw error("CSV_FILE_TOO_LARGE", null, "file", "CSV file exceeds the 2MB limit");
        } catch (IOException | RuntimeException exception) {
            throw error("CSV_MALFORMED", null, "file", "CSV structure is invalid");
        }
    }

    private List<NormalizedTransaction> parseRecords(Iterator<CSVRecord> records, LocalDate today) {
        if (!records.hasNext()) {
            throw error("CSV_EMPTY_FILE", null, "file", "CSV file must contain a header and transactions");
        }
        CSVRecord headerRecord = records.next();
        List<String> headers = values(headerRecord);
        Map<String, Integer> headerIndexes = validateHeaders(headers);
        Set<String> transactionIds = new HashSet<>();
        List<NormalizedTransaction> transactions = new ArrayList<>();
        YearMonth earliest = null;
        YearMonth latest = null;

        while (records.hasNext()) {
            CSVRecord record = records.next();
            int rowNumber = Math.toIntExact(record.getRecordNumber());
            if (record.size() != headers.size()) {
                throw error("CSV_FIELD_COUNT_MISMATCH", rowNumber, "row",
                        "CSV row does not match the header field count");
            }
            if (transactions.size() >= rules.maximumTransactionRows()) {
                throw error("CSV_TOO_MANY_ROWS", rowNumber, "row",
                        "CSV contains more than 10000 transaction rows");
            }
            NormalizedTransaction transaction = parseRecord(record, headerIndexes, transactionIds,
                    rowNumber, today);
            transactions.add(transaction);
            YearMonth month = YearMonth.from(transaction.transactionDate());
            earliest = earliest == null || month.isBefore(earliest) ? month : earliest;
            latest = latest == null || month.isAfter(latest) ? month : latest;
            if (ChronoUnit.MONTHS.between(earliest, latest) + 1L > rules.maximumAnalysisMonths()) {
                throw error("CSV_ANALYSIS_PERIOD_TOO_LONG", rowNumber, TRANSACTION_DATE,
                        "CSV analysis period exceeds 60 months");
            }
        }
        if (transactions.isEmpty()) {
            throw error("CSV_NO_TRANSACTIONS", null, "file", "CSV must contain at least one transaction row");
        }
        return List.copyOf(transactions);
    }

    private Map<String, Integer> validateHeaders(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String header = headers.get(index);
            if (containsControlCharacter(header)) {
                throw error("CSV_CONTROL_CHARACTER", 1, "header", "CSV header contains a control character");
            }
            String lowerHeader = header.toLowerCase(Locale.ROOT);
            if (DIRECT_IDENTIFIER_HEADERS.contains(lowerHeader)) {
                throw error("CSV_DIRECT_IDENTIFIER_HEADER", 1, "header",
                        "Direct identifier headers are not allowed");
            }
            if (!ALLOWED_HEADERS.contains(header)) {
                throw error("CSV_UNKNOWN_HEADER", 1, "header", "CSV contains an unknown header");
            }
            if (indexes.putIfAbsent(header, index) != null) {
                throw error("CSV_DUPLICATE_HEADER", 1, header, "CSV contains a duplicate header");
            }
        }
        for (String required : REQUIRED_HEADERS) {
            if (!indexes.containsKey(required)) {
                throw error("CSV_REQUIRED_HEADER_MISSING", 1, required,
                        "CSV is missing a required header");
            }
        }
        return indexes;
    }

    private NormalizedTransaction parseRecord(CSVRecord record, Map<String, Integer> indexes,
                                              Set<String> transactionIds, int rowNumber, LocalDate today) {
        Map<String, String> fields = new HashMap<>();
        for (Map.Entry<String, Integer> header : indexes.entrySet()) {
            String value = record.get(header.getValue());
            if (containsControlCharacter(value)) {
                throw error("CSV_CONTROL_CHARACTER", rowNumber, header.getKey(),
                        "CSV field contains a control character");
            }
            fields.put(header.getKey(), value);
        }

        LocalDate date = parseDate(fields.get(TRANSACTION_DATE), rowNumber, today);
        TransactionType type = parseType(fields.get(TYPE), rowNumber);
        BigDecimal amount = parseAmount(fields.get(AMOUNT), rowNumber);
        TransactionCategory category = parseCategory(fields.get(CATEGORY), rowNumber);
        String description = fields.get(DESCRIPTION).strip();
        if (description.codePointCount(0, description.length()) > 100) {
            throw error("CSV_DESCRIPTION_TOO_LONG", rowNumber, DESCRIPTION,
                    "Description must be at most 100 characters");
        }
        String transactionId = indexes.containsKey(TRANSACTION_ID)
                ? normalizeTransactionId(fields.get(TRANSACTION_ID), transactionIds, rowNumber) : null;
        return new NormalizedTransaction(date, type, amount, category, description, transactionId);
    }

    private LocalDate parseDate(String raw, int rowNumber, LocalDate today) {
        try {
            LocalDate date = LocalDate.parse(raw.strip());
            if (date.isAfter(today)) {
                throw error("CSV_FUTURE_DATE", rowNumber, TRANSACTION_DATE,
                        "Future transaction dates are not allowed");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw error("CSV_INVALID_DATE", rowNumber, TRANSACTION_DATE,
                    "Transaction date must use yyyy-MM-dd format");
        }
    }

    private TransactionType parseType(String raw, int rowNumber) {
        try {
            return TransactionType.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error("CSV_INVALID_TYPE", rowNumber, TYPE, "Transaction type is not supported");
        }
    }

    private TransactionCategory parseCategory(String raw, int rowNumber) {
        try {
            return TransactionCategory.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw error("CSV_INVALID_CATEGORY", rowNumber, CATEGORY,
                    "Transaction category is not supported");
        }
    }

    private BigDecimal parseAmount(String raw, int rowNumber) {
        try {
            BigDecimal amount = new BigDecimal(raw.strip());
            if (amount.signum() <= 0) {
                throw error("CSV_NON_POSITIVE_AMOUNT", rowNumber, AMOUNT,
                        "Transaction amount must be greater than zero");
            }
            return amount.setScale(rules.moneyScale(), RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            throw error("CSV_INVALID_AMOUNT", rowNumber, AMOUNT, "Transaction amount must be a decimal number");
        }
    }

    private String normalizeTransactionId(String raw, Set<String> transactionIds, int rowNumber) {
        String transactionId = raw.strip();
        if (transactionId.isEmpty()) {
            return null;
        }
        if (transactionId.codePointCount(0, transactionId.length()) > 100) {
            throw error("CSV_TRANSACTION_ID_TOO_LONG", rowNumber, TRANSACTION_ID,
                    "Transaction ID must be at most 100 characters");
        }
        if (!transactionIds.add(transactionId)) {
            throw error("CSV_DUPLICATE_TRANSACTION_ID", rowNumber, TRANSACTION_ID,
                    "Transaction ID must be unique within the file");
        }
        return transactionId;
    }

    private List<String> values(CSVRecord record) {
        List<String> values = new ArrayList<>(record.size());
        for (int index = 0; index < record.size(); index++) {
            values.add(record.get(index));
        }
        return values;
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private PushbackInputStream removeUtf8Bom(InputStream input) throws IOException {
        PushbackInputStream stream = new PushbackInputStream(input, 3);
        byte[] prefix = stream.readNBytes(3);
        boolean bom = prefix.length == 3
                && prefix[0] == (byte) 0xEF && prefix[1] == (byte) 0xBB && prefix[2] == (byte) 0xBF;
        if (!bom && prefix.length > 0) {
            stream.unread(prefix);
        }
        return stream;
    }

    private Reader strictUtf8Reader(InputStream input) {
        return new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT));
    }

    private CsvValidationException ioError(IOException exception) {
        if (exception instanceof SizeLimitIOException) {
            return error("CSV_FILE_TOO_LARGE", null, "file", "CSV file exceeds the 2MB limit");
        }
        if (exception instanceof CharacterCodingException) {
            return error("CSV_INVALID_ENCODING", null, "file", "CSV file must be valid UTF-8");
        }
        return error("CSV_MALFORMED", null, "file", "CSV structure is invalid");
    }

    private CsvValidationException error(String code, Integer row, String column, String message) {
        return new CsvValidationException(code, row, column, message);
    }

    private static final class SizeLimitedInputStream extends FilterInputStream {
        private final long maximumBytes;
        private long bytesRead;

        private SizeLimitedInputStream(InputStream input, long maximumBytes) {
            super(input);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = super.read(bytes, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        private void count(int count) throws SizeLimitIOException {
            bytesRead += count;
            if (bytesRead > maximumBytes) {
                throw new SizeLimitIOException();
            }
        }
    }

    private static final class SizeLimitIOException extends IOException {
    }
}
