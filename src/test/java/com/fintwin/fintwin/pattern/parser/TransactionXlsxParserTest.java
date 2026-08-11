package com.fintwin.fintwin.pattern.parser;

import com.fintwin.fintwin.global.error.XlsxValidationException;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import com.fintwin.fintwin.pattern.support.XlsxFixtures;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionXlsxParserTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);
    private static final List<String> VALID_ROW = List.of(
            "2026-01-01", "INCOME", "1000.10", "SALARY", "Synthetic salary", "id-1");
    private final FinancialPatternRules rules = FinancialPatternRules.standard();
    private final TransactionXlsxParser parser = new TransactionXlsxParser(rules);

    @Test
    void parsesStandardWorkbookToNormalizedTransactionsWithBigDecimalPrecision() {
        List<NormalizedTransaction> transactions = parse(XlsxFixtures.workbook(List.of(VALID_ROW)));

        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().amount()).isEqualByComparingTo("1000.10");
        assertThat(transactions.getFirst().transactionId()).isEqualTo("id-1");
    }

    @Test
    void producesExactlyTheSameNormalizedTransactionsAsCsv() {
        String csv = """
                transactionDate,type,amount,category,description,transactionId
                2026-01-01,INCOME,1000.10,SALARY,Synthetic salary,id-1
                2026-01-02,EXPENSE,125.25,FOOD,Synthetic food,id-2
                """;
        byte[] csvBytes = csv.getBytes(StandardCharsets.UTF_8);
        List<NormalizedTransaction> csvTransactions = new TransactionCsvParser(rules).parse(
                new ByteArrayInputStream(csvBytes), csvBytes.length, TODAY);
        List<NormalizedTransaction> xlsxTransactions = parse(XlsxFixtures.workbook(List.of(
                VALID_ROW,
                List.of("2026-01-02", "EXPENSE", "125.25", "FOOD", "Synthetic food", "id-2"))));

        assertThat(xlsxTransactions).isEqualTo(csvTransactions);
    }

    @Test
    void rejectsMissingDuplicateUnknownAndDirectIdentifierHeaders() {
        assertCode(headers(List.of("transactionDate", "type", "amount", "category", "transactionId")),
                "XLSX_REQUIRED_HEADER_MISSING");
        assertCode(headers(List.of("transactionDate", "type", "amount", "category", "description", "amount")),
                "XLSX_DUPLICATE_HEADER");
        assertCode(headers(List.of("transactionDate", "type", "amount", "category", "description", "memo")),
                "XLSX_UNKNOWN_HEADER");
        assertCode(headers(List.of("transactionDate", "type", "amount", "category", "description", "accountNumber")),
                "XLSX_DIRECT_IDENTIFIER_HEADER");
        assertCode(headers(List.of("transactionDate", "type", "amount", "category", "description", "주민등록번호")),
                "XLSX_DIRECT_IDENTIFIER_HEADER");
    }

    @Test
    void rejectsFormulaHyperlinkCommentMergedAndHiddenContent() {
        assertCode(customSheet(sheet -> sheet.getRow(1).getCell(2).setCellFormula("1+1")),
                "XLSX_FORMULA_NOT_ALLOWED");
        assertCode(XlsxFixtures.workbook(book -> {
            XSSFSheet sheet = standardSheet(book);
            var link = book.getCreationHelper().createHyperlink(HyperlinkType.URL);
            link.setAddress("https://example.invalid/private");
            sheet.getRow(1).getCell(4).setHyperlink(link);
        }), "XLSX_EXTERNAL_LINK_NOT_ALLOWED");
        assertCode(XlsxFixtures.workbook(book -> {
            XSSFSheet sheet = standardSheet(book);
            var drawing = sheet.createDrawingPatriarch();
            var anchor = book.getCreationHelper().createClientAnchor();
            sheet.getRow(1).getCell(4).setCellComment(drawing.createCellComment(anchor));
        }), "XLSX_EMBEDDED_OBJECT_NOT_ALLOWED");
        assertCode(customSheet(sheet -> sheet.addMergedRegion(
                new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 1))),
                "XLSX_MERGED_CELL_NOT_ALLOWED");
        assertCode(customSheet(sheet -> sheet.getRow(1).setZeroHeight(true)), "XLSX_HIDDEN_CONTENT");
        assertCode(customSheet(sheet -> sheet.setColumnHidden(2, true)), "XLSX_HIDDEN_CONTENT");
    }

    @Test
    void rejectsWrongSheetCountAndName() {
        assertCode(XlsxFixtures.workbook(book -> book.createSheet("Sheet1")), "XLSX_INVALID_SHEET");
        assertCode(XlsxFixtures.workbook(book -> {
            standardSheet(book);
            book.createSheet("extra");
        }), "XLSX_INVALID_SHEET");
    }

    @Test
    void rejectsInvalidDatesAmountsTypesCategoriesAndDuplicateIds() {
        assertRowCode(List.of("2026-02-30", "INCOME", "1", "SALARY", "Synthetic", "id-1"),
                "XLSX_INVALID_DATE");
        assertRowCode(List.of("2027-01-01", "INCOME", "1", "SALARY", "Synthetic", "id-1"),
                "XLSX_FUTURE_DATE");
        assertRowCode(List.of("2026-01-01", "EXPENSE", "0", "FOOD", "Synthetic", "id-1"),
                "XLSX_NON_POSITIVE_AMOUNT");
        assertRowCode(List.of("2026-01-01", "EXPENSE", "1e3", "FOOD", "Synthetic", "id-1"),
                "XLSX_INVALID_AMOUNT");
        assertRowCode(List.of("2026-01-01", "UNKNOWN", "1", "FOOD", "Synthetic", "id-1"),
                "XLSX_INVALID_TYPE");
        assertRowCode(List.of("2026-01-01", "EXPENSE", "1", "UNKNOWN", "Synthetic", "id-1"),
                "XLSX_INVALID_CATEGORY");
        assertCode(XlsxFixtures.workbook(List.of(
                VALID_ROW,
                List.of("2026-02-01", "INCOME", "2", "SALARY", "Synthetic", "id-1"))),
                "XLSX_DUPLICATE_TRANSACTION_ID");
    }

    @Test
    void rejectsCorruptDisguisedAndOversizedInputsWithoutLeakingValues() {
        assertCode("not-an-xlsx".getBytes(StandardCharsets.UTF_8), "XLSX_INVALID_WORKBOOK");
        byte[] valid = XlsxFixtures.workbook(List.of(VALID_ROW));
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(valid), rules.maximumFileBytes() + 1, TODAY))
                .isInstanceOfSatisfying(XlsxValidationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("XLSX_FILE_TOO_LARGE"));

        String privateValue = "PRIVATE_SYNTHETIC_DESCRIPTION";
        byte[] workbook = XlsxFixtures.workbook(List.of(
                List.of("2026-01-01", "EXPENSE", "-987654", "FOOD", privateValue, "private-id")));
        assertThatThrownBy(() -> parse(workbook)).isInstanceOfSatisfying(XlsxValidationException.class,
                exception -> assertThat(exception.getMessage())
                        .doesNotContain(privateValue, "987654", "private-id"));
    }

    @Test
    void rejectsMacroPartsExcessiveCompressionRowsAndCells() {
        byte[] standard = XlsxFixtures.workbook(List.of(VALID_ROW));
        assertCode(XlsxFixtures.withArchiveEntry(standard, "xl/vbaProject.bin", new byte[]{1}),
                "XLSX_MACRO_NOT_ALLOWED");
        assertCode(XlsxFixtures.withArchiveEntry(standard, "xl/worksheets/compressed.xml",
                        new byte[2 * 1024 * 1024]),
                "XLSX_ARCHIVE_LIMIT_EXCEEDED");
        assertCode(customSheet(sheet -> sheet.createRow(10_101)), "XLSX_TOO_MANY_ROWS");
        assertCode(customSheet(sheet -> sheet.getRow(1).createCell(16).setCellValue("extra")),
                "XLSX_TOO_MANY_CELLS");
    }

    @Test
    void rejectsMoreThanTenThousandTransactionRows() {
        byte[] workbook = XlsxFixtures.workbook(book -> {
            XSSFSheet sheet = book.createSheet("transactions");
            XlsxFixtures.writeRow(sheet.createRow(0), XlsxFixtures.HEADERS);
            for (int index = 1; index <= 10_001; index++) {
                XlsxFixtures.writeRow(sheet.createRow(index), List.of(
                        "2026-01-01", "EXPENSE", "1", "FOOD", "Synthetic", "id-" + index));
            }
        });

        assertCode(workbook, "XLSX_TOO_MANY_ROWS");
    }

    @Test
    void allowsLimitedBlankRowsButRejectsPartialRowsAndLongValues() {
        byte[] withBlank = XlsxFixtures.workbook(book -> {
            XSSFSheet sheet = standardSheet(book);
            sheet.createRow(2);
            XlsxFixtures.writeRow(sheet.createRow(3),
                    List.of("2026-02-01", "EXPENSE", "10", "FOOD", "Synthetic food", "id-2"));
        });
        assertThat(parse(withBlank)).hasSize(2);

        assertCode(XlsxFixtures.workbook(List.of(
                List.of("", "EXPENSE", "10", "FOOD", "Synthetic", "id-1"))),
                "XLSX_INVALID_DATE");
        assertRowCode(List.of("2026-01-01", "EXPENSE", "10", "FOOD", "x".repeat(101), "id-1"),
                "XLSX_DESCRIPTION_TOO_LONG");
    }

    @Test
    void rejectsAnalysisPeriodOverSixtyMonths() {
        assertCode(XlsxFixtures.workbook(List.of(
                List.of("2021-01-01", "EXPENSE", "1", "FOOD", "Synthetic A", "id-1"),
                List.of("2026-01-01", "EXPENSE", "1", "FOOD", "Synthetic B", "id-2"))),
                "XLSX_ANALYSIS_PERIOD_TOO_LONG");
    }

    private byte[] headers(List<String> headers) {
        return XlsxFixtures.workbook(book -> {
            var sheet = book.createSheet("transactions");
            XlsxFixtures.writeRow(sheet.createRow(0), headers);
            XlsxFixtures.writeRow(sheet.createRow(1), VALID_ROW);
        });
    }

    private byte[] customSheet(java.util.function.Consumer<XSSFSheet> customizer) {
        return XlsxFixtures.workbook(book -> {
            XSSFSheet sheet = standardSheet(book);
            customizer.accept(sheet);
        });
    }

    private XSSFSheet standardSheet(org.apache.poi.xssf.usermodel.XSSFWorkbook book) {
        XSSFSheet sheet = book.createSheet("transactions");
        XlsxFixtures.writeRow(sheet.createRow(0), XlsxFixtures.HEADERS);
        XlsxFixtures.writeRow(sheet.createRow(1), VALID_ROW);
        return sheet;
    }

    private List<NormalizedTransaction> parse(byte[] bytes) {
        return parser.parse(new ByteArrayInputStream(bytes), bytes.length, TODAY);
    }

    private void assertRowCode(List<String> row, String code) {
        assertCode(XlsxFixtures.workbook(List.of(row)), code);
    }

    private void assertCode(byte[] bytes, String code) {
        assertThatThrownBy(() -> parse(bytes)).isInstanceOfSatisfying(XlsxValidationException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
