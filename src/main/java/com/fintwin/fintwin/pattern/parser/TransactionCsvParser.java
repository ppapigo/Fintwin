package com.fintwin.fintwin.pattern.parser;

import com.fintwin.fintwin.global.error.CsvValidationException;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TransactionCsvParser {
    private final FinancialPatternRules rules;
    private final TransactionRecordNormalizer recordNormalizer;

    public TransactionCsvParser(FinancialPatternRules rules) {
        this.rules = Objects.requireNonNull(rules);
        this.recordNormalizer = new TransactionRecordNormalizer(rules);
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
        Map<String, Integer> headerIndexes = recordNormalizer.validateHeaders(headers, "CSV_", this::error);
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
            Map<String, String> fields = new HashMap<>();
            for (Map.Entry<String, Integer> header : headerIndexes.entrySet()) {
                fields.put(header.getKey(), record.get(header.getValue()));
            }
            NormalizedTransaction transaction = recordNormalizer.normalize(fields, transactionIds,
                    rowNumber, today, "CSV_", this::error);
            transactions.add(transaction);
            YearMonth month = YearMonth.from(transaction.transactionDate());
            earliest = earliest == null || month.isBefore(earliest) ? month : earliest;
            latest = latest == null || month.isAfter(latest) ? month : latest;
            if (ChronoUnit.MONTHS.between(earliest, latest) + 1L > rules.maximumAnalysisMonths()) {
                throw error("CSV_ANALYSIS_PERIOD_TOO_LONG", rowNumber,
                        TransactionRecordNormalizer.TRANSACTION_DATE,
                        "CSV analysis period exceeds 60 months");
            }
        }
        if (transactions.isEmpty()) {
            throw error("CSV_NO_TRANSACTIONS", null, "file", "CSV must contain at least one transaction row");
        }
        return List.copyOf(transactions);
    }

    private List<String> values(CSVRecord record) {
        List<String> values = new ArrayList<>(record.size());
        for (int index = 0; index < record.size(); index++) {
            values.add(record.get(index));
        }
        return values;
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
