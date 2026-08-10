package com.fintwin.fintwin.pattern.engine;

import com.fintwin.fintwin.pattern.domain.CategorySpendingPattern;
import com.fintwin.fintwin.pattern.domain.FinancialPatternReport;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.MonthlyCashFlowPattern;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import com.fintwin.fintwin.pattern.domain.PatternWarningCode;
import com.fintwin.fintwin.pattern.domain.RecurringTransactionPattern;
import com.fintwin.fintwin.pattern.domain.SpendingTrend;
import com.fintwin.fintwin.pattern.domain.TransactionCategory;
import com.fintwin.fintwin.pattern.domain.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialPatternEngineTest {
    private final FinancialPatternEngine engine = new FinancialPatternEngine(FinancialPatternRules.standard());

    @Test
    void calculatesMonthlyCashFlowAndIncludesEmptyIntermediateMonths() {
        List<NormalizedTransaction> transactions = List.of(
                tx("2026-01-01", TransactionType.INCOME, "1000", TransactionCategory.SALARY, "Income"),
                tx("2026-01-02", TransactionType.EXPENSE, "400", TransactionCategory.FOOD, "Food"),
                tx("2026-01-03", TransactionType.SAVING_TRANSFER, "100", TransactionCategory.SAVINGS, "Saving"),
                tx("2026-01-04", TransactionType.INVESTMENT_TRANSFER, "50", TransactionCategory.INVESTMENT, "Invest"),
                tx("2026-01-05", TransactionType.DEBT_PAYMENT, "100", TransactionCategory.DEBT, "Debt"),
                tx("2026-01-06", TransactionType.TRANSFER, "200", TransactionCategory.OTHER, "Internal"),
                tx("2026-03-01", TransactionType.INCOME, "900", TransactionCategory.SALARY, "Income March"),
                tx("2026-03-02", TransactionType.EXPENSE, "300", TransactionCategory.FOOD, "Food March")
        );

        FinancialPatternReport report = engine.analyze(transactions);

        assertThat(report.includedMonthCount()).isEqualTo(3);
        assertThat(report.monthlyCashFlows()).extracting(MonthlyCashFlowPattern::yearMonth)
                .containsExactly(YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3));
        MonthlyCashFlowPattern january = report.monthlyCashFlows().getFirst();
        assertMoney(january.income(), "1000.00");
        assertMoney(january.expenses(), "400.00");
        assertMoney(january.debtPayments(), "100.00");
        assertMoney(january.transfers(), "200.00");
        assertMoney(january.monthlySurplus(), "500.00");
        assertMoney(january.liquidityAfterAllocations(), "350.00");
        MonthlyCashFlowPattern february = report.monthlyCashFlows().get(1);
        assertMoney(february.income(), "0.00");
        assertMoney(february.expenses(), "0.00");
        assertThat(february.transactionCount()).isZero();
        assertMoney(report.averages().averageMonthlySavingTransfers(), "33.33");
        assertMoney(report.averages().averageMonthlyInvestmentTransfers(), "16.67");
    }

    @Test
    void detectsRecurringExpensesUsingNormalizedDescriptionAndMedianTolerance() {
        List<NormalizedTransaction> transactions = new ArrayList<>();
        transactions.add(tx("2026-01-01", TransactionType.EXPENSE, "100", TransactionCategory.HOUSING,
                " Synthetic   Rent "));
        transactions.add(tx("2026-02-01", TransactionType.EXPENSE, "105", TransactionCategory.HOUSING,
                "synthetic rent"));
        transactions.add(tx("2026-03-01", TransactionType.EXPENSE, "95", TransactionCategory.HOUSING,
                "SYNTHETIC RENT"));
        transactions.add(tx("2026-01-10", TransactionType.EXPENSE, "50", TransactionCategory.FOOD, "One January"));
        transactions.add(tx("2026-02-10", TransactionType.EXPENSE, "50", TransactionCategory.FOOD, "One February"));
        transactions.add(tx("2026-03-10", TransactionType.EXPENSE, "50", TransactionCategory.FOOD, "One March"));
        transactions.add(tx("2026-01-20", TransactionType.EXPENSE, "100", TransactionCategory.OTHER, "Too Variable"));
        transactions.add(tx("2026-02-20", TransactionType.EXPENSE, "100", TransactionCategory.OTHER, "Too Variable"));
        transactions.add(tx("2026-03-20", TransactionType.EXPENSE, "111", TransactionCategory.OTHER, "Too Variable"));

        FinancialPatternReport report = engine.analyze(transactions);

        assertThat(report.recurringTransactions()).hasSize(1);
        RecurringTransactionPattern recurring = report.recurringTransactions().getFirst();
        assertThat(recurring.displayDescription()).isEqualTo("Synthetic Rent");
        assertThat(recurring.detectedMonthCount()).isEqualTo(3);
        assertThat(recurring.totalOccurrenceCount()).isEqualTo(3);
        assertMoney(recurring.averageMonthlyAmount(), "100.00");
        assertPercent(recurring.amountVariationRatePercent(), "10.0000");
        assertThat(recurring.fixedExpenseCandidate()).isTrue();
        assertMoney(report.expenseClassification().estimatedFixedExpenses(), "100.00");
        assertMoney(report.expenseClassification().estimatedVariableExpenses(), "153.67");
        assertThat(report.expenseClassification().dataConfidence()).isEqualTo("MEDIUM");
    }

    @Test
    void usesPerMonthRepresentativeForMultipleRecurringOccurrences() {
        List<NormalizedTransaction> transactions = new ArrayList<>();
        for (int month = 1; month <= 3; month++) {
            String day = "2026-%02d-01".formatted(month);
            String otherDay = "2026-%02d-15".formatted(month);
            transactions.add(tx(day, TransactionType.EXPENSE, "50", TransactionCategory.OTHER, "Synthetic Class"));
            transactions.add(tx(otherDay, TransactionType.EXPENSE, "50", TransactionCategory.OTHER, "Synthetic Class"));
        }

        RecurringTransactionPattern recurring = engine.analyze(transactions).recurringTransactions().getFirst();

        assertThat(recurring.totalOccurrenceCount()).isEqualTo(6);
        assertPercent(recurring.averageOccurrencesPerMonth(), "2.0000");
        assertMoney(recurring.averageMonthlyAmount(), "100.00");
        assertPercent(recurring.amountVariationRatePercent(), "0.0000");
    }

    @Test
    void calculatesCategoriesMadTrendAndHighestLowestMonths() {
        List<NormalizedTransaction> transactions = new ArrayList<>();
        String[] expenses = {"100", "200", "300", "400"};
        for (int index = 0; index < expenses.length; index++) {
            int month = index + 1;
            transactions.add(tx("2026-%02d-01".formatted(month), TransactionType.INCOME, "1000",
                    TransactionCategory.SALARY, "Income " + month));
            TransactionCategory category = month <= 2 ? TransactionCategory.FOOD : TransactionCategory.SHOPPING;
            transactions.add(tx("2026-%02d-02".formatted(month), TransactionType.EXPENSE, expenses[index],
                    category, "Expense " + month));
        }

        FinancialPatternReport report = engine.analyze(transactions);

        assertMoney(report.averages().averageMonthlyExpenses(), "250.00");
        assertPercent(report.averages().savingsRatePercent(), "75.0000");
        assertThat(report.averages().highestExpenseMonth()).isEqualTo(YearMonth.of(2026, 4));
        assertThat(report.averages().lowestExpenseMonth()).isEqualTo(YearMonth.of(2026, 1));
        assertMoney(report.volatility().expenseMeanAbsoluteDeviation(), "100.00");
        assertPercent(report.volatility().expenseVolatilityRatioPercent(), "40.0000");
        assertThat(report.spendingTrend().trend()).isEqualTo(SpendingTrend.INCREASING);
        assertPercent(report.spendingTrend().trendRatePercent(), "133.3333");
        CategorySpendingPattern food = category(report, TransactionCategory.FOOD);
        CategorySpendingPattern shopping = category(report, TransactionCategory.SHOPPING);
        assertMoney(food.totalExpenses(), "300.00");
        assertPercent(food.spendingRatioPercent(), "30.0000");
        assertMoney(shopping.totalExpenses(), "700.00");
        assertPercent(shopping.spendingRatioPercent(), "70.0000");
        assertThat(warningCodes(report)).contains(PatternWarningCode.HIGH_EXPENSE_VOLATILITY);
    }

    @Test
    void classifiesDecreasingStableAndInsufficientSpendingTrends() {
        FinancialPatternReport decreasing = engine.analyze(expensesByMonth("300", "100", "100"));
        FinancialPatternReport stable = engine.analyze(expensesByMonth("100", "104", "104"));
        FinancialPatternReport insufficient = engine.analyze(expensesByMonth("100", "200"));

        assertThat(decreasing.spendingTrend().trend()).isEqualTo(SpendingTrend.DECREASING);
        assertThat(stable.spendingTrend().trend()).isEqualTo(SpendingTrend.STABLE);
        assertThat(insufficient.spendingTrend().trend()).isEqualTo(SpendingTrend.INSUFFICIENT_DATA);
        assertThat(insufficient.spendingTrend().trendRatePercent()).isNull();
    }

    @Test
    void emitsDeterministicWarningsForEdgeCases() {
        FinancialPatternReport noIncome = engine.analyze(List.of(
                tx("2026-01-01", TransactionType.EXPENSE, "100", TransactionCategory.UNCATEGORIZED, "Unknown"),
                tx("2026-02-01", TransactionType.EXPENSE, "100", TransactionCategory.UNCATEGORIZED, "Unknown"),
                tx("2026-03-01", TransactionType.EXPENSE, "100", TransactionCategory.UNCATEGORIZED, "Unknown")
        ));
        FinancialPatternReport lowSavings = engine.analyze(List.of(
                tx("2026-01-01", TransactionType.INCOME, "100", TransactionCategory.SALARY, "Income"),
                tx("2026-01-02", TransactionType.EXPENSE, "95", TransactionCategory.FOOD, "Expense")
        ));
        FinancialPatternReport noExpenses = engine.analyze(List.of(
                tx("2026-01-01", TransactionType.INCOME, "100", TransactionCategory.SALARY, "Income one"),
                tx("2026-02-01", TransactionType.INCOME, "100", TransactionCategory.SALARY, "Income two")
        ));

        assertThat(warningCodes(noIncome)).contains(
                PatternWarningCode.NO_INCOME_FOUND,
                PatternWarningCode.NEGATIVE_CASH_FLOW_MONTHS,
                PatternWarningCode.MANY_UNCATEGORIZED_TRANSACTIONS,
                PatternWarningCode.PROFILE_REVIEW_REQUIRED);
        assertThat(noIncome.deficitMonths()).containsExactly(
                YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3));
        assertThat(noIncome.averages().savingsRatePercent()).isNull();
        assertThat(warningCodes(lowSavings)).contains(
                PatternWarningCode.INSUFFICIENT_HISTORY,
                PatternWarningCode.LOW_SAVINGS_RATE,
                PatternWarningCode.RECURRING_PATTERN_UNAVAILABLE,
                PatternWarningCode.PROFILE_REVIEW_REQUIRED);
        assertThat(warningCodes(noExpenses)).contains(PatternWarningCode.NO_EXPENSE_FOUND);
    }

    @Test
    void capsFixedExpenseEstimateAndNeverProducesNegativeVariableExpense() {
        List<NormalizedTransaction> transactions = List.of(
                tx("2026-01-01", TransactionType.EXPENSE, "100", TransactionCategory.HOUSING, "Short Rent"),
                tx("2026-02-01", TransactionType.EXPENSE, "100", TransactionCategory.HOUSING, "Short Rent"),
                tx("2026-03-01", TransactionType.EXPENSE, "100", TransactionCategory.HOUSING, "Short Rent"),
                tx("2026-06-01", TransactionType.INCOME, "1", TransactionCategory.SALARY, "Period Marker")
        );

        FinancialPatternReport report = engine.analyze(transactions);

        assertThat(report.includedMonthCount()).isEqualTo(6);
        assertMoney(report.averages().averageMonthlyExpenses(), "50.00");
        assertMoney(report.expenseClassification().estimatedFixedExpenses(), "50.00");
        assertMoney(report.expenseClassification().estimatedVariableExpenses(), "0.00");
    }

    @Test
    void producesSameReportRegardlessOfInputOrderAndRejectsEmptyInput() {
        List<NormalizedTransaction> source = new ArrayList<>(expensesByMonth("100", "200", "300", "400"));
        FinancialPatternReport ordered = engine.analyze(source);
        Collections.reverse(source);

        assertThat(engine.analyze(source)).isEqualTo(ordered);
        assertThatThrownBy(() -> engine.analyze(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transactions must not be empty");
    }

    private List<NormalizedTransaction> expensesByMonth(String... amounts) {
        List<NormalizedTransaction> transactions = new ArrayList<>();
        for (int index = 0; index < amounts.length; index++) {
            transactions.add(tx("2026-%02d-01".formatted(index + 1), TransactionType.EXPENSE,
                    amounts[index], TransactionCategory.FOOD, "Expense " + index));
        }
        return transactions;
    }

    private CategorySpendingPattern category(FinancialPatternReport report, TransactionCategory category) {
        return report.categorySpending().stream()
                .filter(pattern -> pattern.category() == category)
                .findFirst()
                .orElseThrow();
    }

    private List<PatternWarningCode> warningCodes(FinancialPatternReport report) {
        return report.warnings().stream().map(warning -> warning.code()).toList();
    }

    private NormalizedTransaction tx(String date, TransactionType type, String amount,
                                     TransactionCategory category, String description) {
        return new NormalizedTransaction(LocalDate.parse(date), type, new BigDecimal(amount), category,
                description, null);
    }

    private void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    private void assertPercent(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }
}
