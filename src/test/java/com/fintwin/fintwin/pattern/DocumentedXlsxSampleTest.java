package com.fintwin.fintwin.pattern;

import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.parser.TransactionXlsxParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentedXlsxSampleTest {
    @Test
    void documentedSixMonthSyntheticWorkbookIsAcceptedByTheProductionParser() throws Exception {
        byte[] workbook = Files.readAllBytes(Path.of(
                "docs", "examples", "fintwin-transactions-sample.xlsx"));

        var transactions = new TransactionXlsxParser(FinancialPatternRules.standard()).parse(
                new ByteArrayInputStream(workbook), workbook.length, LocalDate.of(2026, 12, 31));

        assertThat(transactions).hasSize(37);
        assertThat(transactions.getFirst().transactionDate()).hasToString("2026-01-25");
        assertThat(transactions.getLast().transactionDate()).hasToString("2026-06-30");
        assertThat(transactions).allSatisfy(transaction ->
                assertThat(transaction.description()).startsWith("Synthetic"));
    }
}
