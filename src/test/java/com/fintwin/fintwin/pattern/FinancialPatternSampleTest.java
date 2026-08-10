package com.fintwin.fintwin.pattern;

import com.fintwin.fintwin.pattern.domain.FinancialPatternReport;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.SpendingTrend;
import com.fintwin.fintwin.pattern.engine.FinancialPatternEngine;
import com.fintwin.fintwin.pattern.parser.TransactionCsvParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialPatternSampleTest {
    @Test
    void documentedSampleProducesDocumentedAggregates() throws Exception {
        FinancialPatternRules rules = FinancialPatternRules.standard();
        Path sample = Path.of("docs", "examples", "fintwin-transactions-sample.csv");

        FinancialPatternReport report;
        try (InputStream input = Files.newInputStream(sample)) {
            var transactions = new TransactionCsvParser(rules)
                    .parse(input, Files.size(sample), LocalDate.of(2026, 12, 31));
            report = new FinancialPatternEngine(rules).analyze(transactions);
        }

        assertThat(report.transactionCount()).isEqualTo(37);
        assertThat(report.includedMonthCount()).isEqualTo(6);
        assertThat(report.averages().averageMonthlyIncome()).isEqualByComparingTo("3000000.00");
        assertThat(report.averages().averageMonthlyExpenses()).isEqualByComparingTo("1355000.00");
        assertThat(report.averages().averageMonthlySavingTransfers()).isEqualByComparingTo("500000.00");
        assertThat(report.averages().averageMonthlyInvestmentTransfers()).isEqualByComparingTo("300000.00");
        assertThat(report.averages().averageMonthlySurplus()).isEqualByComparingTo("1645000.00");
        assertThat(report.averages().averageMonthlyLiquidityAfterAllocations())
                .isEqualByComparingTo("845000.00");
        assertThat(report.averages().savingsRatePercent()).isEqualByComparingTo("57.3333");
        assertThat(report.expenseClassification().estimatedFixedExpenses())
                .isEqualByComparingTo("700000.00");
        assertThat(report.expenseClassification().estimatedVariableExpenses())
                .isEqualByComparingTo("655000.00");
        assertThat(report.spendingTrend().trend()).isEqualTo(SpendingTrend.INCREASING);
        assertThat(report.deficitMonths()).containsExactly(YearMonth.of(2026, 6));
    }
}
