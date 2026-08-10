package com.fintwin.fintwin.pattern.domain;

import java.time.YearMonth;
import java.util.List;

public record FinancialPatternReport(
        YearMonth analysisStartYearMonth,
        YearMonth analysisEndYearMonth,
        int includedMonthCount,
        int transactionCount,
        List<MonthlyCashFlowPattern> monthlyCashFlows,
        AverageFinancialPattern averages,
        List<CategorySpendingPattern> categorySpending,
        List<RecurringTransactionPattern> recurringTransactions,
        ExpenseClassificationEstimate expenseClassification,
        FinancialVolatilityPattern volatility,
        SpendingTrendPattern spendingTrend,
        List<YearMonth> deficitMonths,
        FinancialProfileDraft profileDraft,
        FinancialPatternRules analysisRules,
        List<PatternWarning> warnings
) {
    public FinancialPatternReport {
        monthlyCashFlows = List.copyOf(monthlyCashFlows);
        categorySpending = List.copyOf(categorySpending);
        recurringTransactions = List.copyOf(recurringTransactions);
        deficitMonths = List.copyOf(deficitMonths);
        warnings = List.copyOf(warnings);
    }
}
