package com.fintwin.fintwin.pattern.parser;

import com.fintwin.fintwin.global.error.CsvValidationException;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionCsvParserTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);
    private static final String HEADER = "transactionDate,type,amount,category,description,transactionId\r\n";
    private final FinancialPatternRules rules = FinancialPatternRules.standard();
    private final TransactionCsvParser parser = new TransactionCsvParser(rules);

    @Test
    void parsesValidUtf8CsvAndOptionalTransactionIdHeader() {
        List<NormalizedTransaction> withId = parse(HEADER
                + "2026-01-01,INCOME,1000,SALARY,Synthetic salary,id-1\r\n");
        List<NormalizedTransaction> withoutId = parse(
                "transactionDate,type,amount,category,description\r\n"
                        + "2026-01-02,EXPENSE,10,FOOD,Synthetic meal\r\n");

        assertThat(withId).hasSize(1);
        assertThat(withId.getFirst().amount()).isEqualByComparingTo("1000.00");
        assertThat(withId.getFirst().transactionId()).isEqualTo("id-1");
        assertThat(withoutId.getFirst().transactionId()).isNull();
    }

    @Test
    void acceptsUtf8Bom() {
        byte[] content = (HEADER + "2026-01-01,INCOME,1000,SALARY,Synthetic salary,id-1\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[content.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(content, 0, bytes, 3, content.length);

        assertThat(parser.parse(new ByteArrayInputStream(bytes), bytes.length, TODAY)).hasSize(1);
    }

    @Test
    void parsesCommaAndEscapedQuoteInsideDescription() {
        List<NormalizedTransaction> transactions = parse(HEADER
                + "2026-01-01,EXPENSE,10,FOOD,\"Synthetic, lunch\",id-1\r\n"
                + "2026-01-02,EXPENSE,20,SHOPPING,\"Synthetic \"\"quoted\"\" item\",id-2\r\n");

        assertThat(transactions).extracting(NormalizedTransaction::description)
                .containsExactly("Synthetic, lunch", "Synthetic \"quoted\" item");
    }

    @Test
    void rejectsEmptyAndOversizedFile() {
        assertCode(new byte[0], 0, "CSV_EMPTY_FILE");
        byte[] content = validCsv().getBytes(StandardCharsets.UTF_8);
        assertCode(content, rules.maximumFileBytes() + 1, "CSV_FILE_TOO_LARGE");
    }

    @Test
    void rejectsMoreThanTenThousandRows() {
        StringBuilder csv = new StringBuilder("transactionDate,type,amount,category,description\r\n");
        for (int row = 0; row <= rules.maximumTransactionRows(); row++) {
            csv.append("2026-01-01,EXPENSE,1,OTHER,Synthetic row\r\n");
        }

        assertThatThrownBy(() -> parse(csv.toString()))
                .isInstanceOfSatisfying(CsvValidationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("CSV_TOO_MANY_ROWS"));
    }

    @Test
    void rejectsMissingDuplicateUnknownAndDirectIdentifierHeaders() {
        assertCode("transactionDate,type,amount,category\r\n"
                + "2026-01-01,INCOME,1,SALARY\r\n", "CSV_REQUIRED_HEADER_MISSING");
        assertCode("transactionDate,type,amount,category,description,amount\r\n"
                + "2026-01-01,INCOME,1,SALARY,Synthetic,1\r\n", "CSV_DUPLICATE_HEADER");
        assertCode("transactionDate,type,amount,category,description,unknownField\r\n"
                + "2026-01-01,INCOME,1,SALARY,Synthetic,x\r\n", "CSV_UNKNOWN_HEADER");
        assertCode("transactionDate,type,amount,category,description,accountNumber\r\n"
                + "2026-01-01,INCOME,1,SALARY,Synthetic,x\r\n", "CSV_DIRECT_IDENTIFIER_HEADER");
    }

    @Test
    void rejectsFieldCountMismatch() {
        assertCode(HEADER + "2026-01-01,INCOME,1,SALARY,Synthetic\r\n",
                "CSV_FIELD_COUNT_MISMATCH");
    }

    @Test
    void rejectsInvalidAndFutureDates() {
        assertCode(HEADER + "2026-02-30,INCOME,1,SALARY,Synthetic,id-1\r\n", "CSV_INVALID_DATE");
        assertCode(HEADER + "2027-01-01,INCOME,1,SALARY,Synthetic,id-1\r\n", "CSV_FUTURE_DATE");
    }

    @Test
    void rejectsZeroNegativeAndMalformedAmounts() {
        assertCode(HEADER + "2026-01-01,EXPENSE,0,FOOD,Synthetic,id-1\r\n",
                "CSV_NON_POSITIVE_AMOUNT");
        assertCode(HEADER + "2026-01-01,EXPENSE,-1,FOOD,Synthetic,id-1\r\n",
                "CSV_NON_POSITIVE_AMOUNT");
        assertCode(HEADER + "2026-01-01,EXPENSE,not-number,FOOD,Synthetic,id-1\r\n",
                "CSV_INVALID_AMOUNT");
    }

    @Test
    void rejectsUnsupportedTypeAndCategory() {
        assertCode(HEADER + "2026-01-01,UNKNOWN,1,FOOD,Synthetic,id-1\r\n", "CSV_INVALID_TYPE");
        assertCode(HEADER + "2026-01-01,EXPENSE,1,UNKNOWN,Synthetic,id-1\r\n",
                "CSV_INVALID_CATEGORY");
    }

    @Test
    void rejectsDuplicateTransactionIdButAllowsMissingIds() {
        assertCode(HEADER
                + "2026-01-01,EXPENSE,1,FOOD,Synthetic A,id-1\r\n"
                + "2026-01-02,EXPENSE,2,FOOD,Synthetic B,id-1\r\n", "CSV_DUPLICATE_TRANSACTION_ID");
        assertThat(parse(HEADER
                + "2026-01-01,EXPENSE,1,FOOD,Synthetic A,\r\n"
                + "2026-01-02,EXPENSE,2,FOOD,Synthetic B,\r\n")).hasSize(2);
    }

    @Test
    void rejectsControlCharactersAndInvalidUtf8() {
        assertCode(HEADER + "2026-01-01,EXPENSE,1,FOOD,\"Synthetic\nitem\",id-1\r\n",
                "CSV_CONTROL_CHARACTER");
        byte[] invalidUtf8 = new byte[] {(byte) 0xC3, (byte) 0x28};
        assertCode(invalidUtf8, invalidUtf8.length, "CSV_INVALID_ENCODING");
    }

    @Test
    void rejectsAnalysisPeriodOverSixtyMonths() {
        assertCode(HEADER
                + "2021-01-01,EXPENSE,1,FOOD,Synthetic A,id-1\r\n"
                + "2026-01-01,EXPENSE,1,FOOD,Synthetic B,id-2\r\n",
                "CSV_ANALYSIS_PERIOD_TOO_LONG");
    }

    @Test
    void validationErrorDoesNotExposeTransactionValues() {
        String privateDescription = "PRIVATE_SYNTHETIC_DESCRIPTION";
        String privateId = "PRIVATE_SYNTHETIC_ID";
        String privateAmount = "987654321";

        assertThatThrownBy(() -> parse(HEADER + "2026-01-01,EXPENSE,-" + privateAmount
                + ",FOOD," + privateDescription + "," + privateId + "\r\n"))
                .isInstanceOfSatisfying(CsvValidationException.class, exception -> {
                    assertThat(exception.getMessage()).doesNotContain(privateDescription, privateId, privateAmount);
                    assertThat(exception.getRowNumber()).isEqualTo(2);
                    assertThat(exception.getColumnName()).isEqualTo("amount");
                });
    }

    private List<NormalizedTransaction> parse(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return parser.parse(new ByteArrayInputStream(bytes), bytes.length, TODAY);
    }

    private void assertCode(String csv, String code) {
        assertThatThrownBy(() -> parse(csv)).isInstanceOfSatisfying(CsvValidationException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    private void assertCode(byte[] bytes, long declaredSize, String code) {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(bytes), declaredSize, TODAY))
                .isInstanceOfSatisfying(CsvValidationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }

    private String validCsv() {
        return HEADER + "2026-01-01,INCOME,1000,SALARY,Synthetic salary,id-1\r\n";
    }
}
