package com.fintwin.fintwin.pattern.engine;

import com.fintwin.fintwin.pattern.domain.AverageFinancialPattern;
import com.fintwin.fintwin.pattern.domain.CategorySpendingPattern;
import com.fintwin.fintwin.pattern.domain.ExpenseClassificationEstimate;
import com.fintwin.fintwin.pattern.domain.FinancialPatternReport;
import com.fintwin.fintwin.pattern.domain.FinancialPatternRules;
import com.fintwin.fintwin.pattern.domain.FinancialProfileDraft;
import com.fintwin.fintwin.pattern.domain.FinancialVolatilityPattern;
import com.fintwin.fintwin.pattern.domain.MonthlyCashFlowPattern;
import com.fintwin.fintwin.pattern.domain.NormalizedTransaction;
import com.fintwin.fintwin.pattern.domain.PatternWarning;
import com.fintwin.fintwin.pattern.domain.PatternWarningCode;
import com.fintwin.fintwin.pattern.domain.RecurringTransactionPattern;
import com.fintwin.fintwin.pattern.domain.SpendingTrend;
import com.fintwin.fintwin.pattern.domain.SpendingTrendPattern;
import com.fintwin.fintwin.pattern.domain.TransactionCategory;
import com.fintwin.fintwin.pattern.domain.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class FinancialPatternEngine {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final FinancialPatternRules rules;

    public FinancialPatternEngine(FinancialPatternRules rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    public FinancialPatternReport analyze(List<NormalizedTransaction> sourceTransactions) {
        if (sourceTransactions == null || sourceTransactions.isEmpty()) {
            throw new IllegalArgumentException("transactions must not be empty");
        }
        List<NormalizedTransaction> transactions = sourceTransactions.stream()
                .sorted(transactionOrder())
                .toList();
        YearMonth start = YearMonth.from(transactions.getFirst().transactionDate());
        YearMonth end = YearMonth.from(transactions.getLast().transactionDate());
        int monthCount = Math.toIntExact(ChronoUnit.MONTHS.between(start, end) + 1L);

        Map<YearMonth, MonthlyAccumulator> monthly = new LinkedHashMap<>();
        for (int offset = 0; offset < monthCount; offset++) {
            monthly.put(start.plusMonths(offset), new MonthlyAccumulator());
        }
        Map<TransactionCategory, BigDecimal> categoryExpenses = new EnumMap<>(TransactionCategory.class);
        for (NormalizedTransaction transaction : transactions) {
            MonthlyAccumulator accumulator = monthly.get(YearMonth.from(transaction.transactionDate()));
            accumulator.add(transaction);
            if (transaction.type() == TransactionType.EXPENSE) {
                categoryExpenses.merge(transaction.category(), transaction.amount(), BigDecimal::add);
            }
        }

        List<MonthlyCashFlowPattern> monthlyPatterns = monthly.entrySet().stream()
                .map(entry -> entry.getValue().toPattern(entry.getKey()))
                .toList();
        AverageFinancialPattern averages = calculateAverages(monthlyPatterns);
        List<CategorySpendingPattern> categoryPatterns = calculateCategories(categoryExpenses,
                averages.averageMonthlyExpenses(), monthCount);
        List<RecurringTransactionPattern> recurring = detectRecurring(transactions);
        ExpenseClassificationEstimate expenseEstimate = estimateExpenses(averages, recurring, monthCount);
        FinancialVolatilityPattern volatility = calculateVolatility(monthlyPatterns, averages);
        SpendingTrendPattern trend = calculateTrend(monthlyPatterns);
        List<YearMonth> deficitMonths = monthlyPatterns.stream()
                .filter(month -> month.monthlySurplus().signum() < 0)
                .map(MonthlyCashFlowPattern::yearMonth)
                .toList();
        FinancialProfileDraft draft = createDraft(start, end, monthCount, averages, expenseEstimate);
        List<PatternWarning> warnings = createWarnings(monthCount, averages, categoryExpenses,
                recurring, volatility, deficitMonths);

        return new FinancialPatternReport(start, end, monthCount, transactions.size(), monthlyPatterns,
                averages, categoryPatterns, recurring, expenseEstimate, volatility, trend, deficitMonths,
                draft, rules, warnings);
    }

    private AverageFinancialPattern calculateAverages(List<MonthlyCashFlowPattern> months) {
        int count = months.size();
        BigDecimal income = average(months.stream().map(MonthlyCashFlowPattern::income).toList(), count);
        BigDecimal expenses = average(months.stream().map(MonthlyCashFlowPattern::expenses).toList(), count);
        BigDecimal savings = average(months.stream().map(MonthlyCashFlowPattern::savingTransfers).toList(), count);
        BigDecimal investments = average(months.stream()
                .map(MonthlyCashFlowPattern::investmentTransfers).toList(), count);
        BigDecimal debt = average(months.stream().map(MonthlyCashFlowPattern::debtPayments).toList(), count);
        BigDecimal surplus = average(months.stream().map(MonthlyCashFlowPattern::monthlySurplus).toList(), count);
        BigDecimal liquidity = average(months.stream()
                .map(MonthlyCashFlowPattern::liquidityAfterAllocations).toList(), count);
        BigDecimal positiveSurplusAverage = average(months.stream()
                .map(month -> month.monthlySurplus().max(BigDecimal.ZERO)).toList(), count);
        BigDecimal savingsRate = income.signum() == 0 ? null : percent(positiveSurplusAverage, income);
        MonthlyCashFlowPattern highest = months.stream()
                .max(Comparator.comparing(MonthlyCashFlowPattern::expenses)
                        .thenComparing(MonthlyCashFlowPattern::yearMonth, Comparator.reverseOrder()))
                .orElseThrow();
        MonthlyCashFlowPattern lowest = months.stream()
                .min(Comparator.comparing(MonthlyCashFlowPattern::expenses)
                        .thenComparing(MonthlyCashFlowPattern::yearMonth))
                .orElseThrow();
        int deficitCount = (int) months.stream().filter(month -> month.monthlySurplus().signum() < 0).count();
        BigDecimal deficitRatio = ratio(BigDecimal.valueOf(deficitCount), BigDecimal.valueOf(count));
        return new AverageFinancialPattern(income, expenses, savings, investments, debt, surplus, liquidity,
                savingsRate, highest.yearMonth(), highest.expenses(), lowest.yearMonth(), lowest.expenses(),
                deficitCount, deficitRatio);
    }

    private List<CategorySpendingPattern> calculateCategories(Map<TransactionCategory, BigDecimal> totals,
                                                               BigDecimal averageExpenses, int monthCount) {
        BigDecimal totalExpenses = money(averageExpenses.multiply(BigDecimal.valueOf(monthCount)));
        return totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new CategorySpendingPattern(entry.getKey(), money(entry.getValue()),
                        divide(entry.getValue(), BigDecimal.valueOf(monthCount)),
                        totalExpenses.signum() == 0 ? null : percent(entry.getValue(), totalExpenses)))
                .toList();
    }

    private List<RecurringTransactionPattern> detectRecurring(List<NormalizedTransaction> transactions) {
        Map<RecurringKey, List<NormalizedTransaction>> groups = new HashMap<>();
        for (NormalizedTransaction transaction : transactions) {
            String normalizedDescription = normalizeDescription(transaction.description());
            if (!normalizedDescription.isEmpty()) {
                RecurringKey key = new RecurringKey(normalizedDescription, transaction.type(),
                        transaction.category());
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(transaction);
            }
        }

        List<RecurringTransactionPattern> patterns = new ArrayList<>();
        for (Map.Entry<RecurringKey, List<NormalizedTransaction>> group : groups.entrySet()) {
            Map<YearMonth, List<NormalizedTransaction>> byMonth = new LinkedHashMap<>();
            group.getValue().forEach(transaction -> byMonth
                    .computeIfAbsent(YearMonth.from(transaction.transactionDate()), ignored -> new ArrayList<>())
                    .add(transaction));
            if (byMonth.size() < rules.recurringMinimumDistinctMonths()) {
                continue;
            }
            List<BigDecimal> representatives = new ArrayList<>();
            List<BigDecimal> monthlyTotals = new ArrayList<>();
            int occurrenceCount = 0;
            for (List<NormalizedTransaction> monthlyTransactions : byMonth.values()) {
                BigDecimal total = monthlyTransactions.stream().map(NormalizedTransaction::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                monthlyTotals.add(money(total));
                representatives.add(divide(total, BigDecimal.valueOf(monthlyTransactions.size())));
                occurrenceCount += monthlyTransactions.size();
            }
            BigDecimal median = median(representatives);
            boolean withinTolerance = representatives.stream().allMatch(amount ->
                    percent(amount.subtract(median).abs(), median)
                            .compareTo(rules.recurringAmountTolerancePercent()) <= 0);
            if (!withinTolerance) {
                continue;
            }
            BigDecimal minimum = representatives.stream().min(BigDecimal::compareTo).orElseThrow();
            BigDecimal maximum = representatives.stream().max(BigDecimal::compareTo).orElseThrow();
            BigDecimal variation = percent(maximum.subtract(minimum), median);
            String displayDescription = displayDescription(group.getValue().getFirst().description());
            patterns.add(new RecurringTransactionPattern(group.getKey().type(), group.getKey().category(),
                    displayDescription, byMonth.size(), occurrenceCount,
                    ratioValue(BigDecimal.valueOf(occurrenceCount), BigDecimal.valueOf(byMonth.size())),
                    average(monthlyTotals, monthlyTotals.size()), variation,
                    byMonth.keySet().stream().max(YearMonth::compareTo).orElseThrow(),
                    group.getKey().type() == TransactionType.EXPENSE));
        }
        return patterns.stream()
                .sorted(Comparator.comparing(RecurringTransactionPattern::type)
                        .thenComparing(RecurringTransactionPattern::category)
                        .thenComparing(RecurringTransactionPattern::displayDescription))
                .toList();
    }

    private ExpenseClassificationEstimate estimateExpenses(AverageFinancialPattern averages,
                                                             List<RecurringTransactionPattern> recurring,
                                                             int monthCount) {
        BigDecimal fixed = recurring.stream()
                .filter(RecurringTransactionPattern::fixedExpenseCandidate)
                .map(RecurringTransactionPattern::averageMonthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        fixed = money(fixed.min(averages.averageMonthlyExpenses()));
        BigDecimal variable = money(averages.averageMonthlyExpenses().subtract(fixed).max(BigDecimal.ZERO));
        String confidence = monthCount >= rules.highConfidenceMinimumMonths() ? "HIGH"
                : monthCount >= rules.recurringMinimumDistinctMonths() ? "MEDIUM" : "LOW";
        return new ExpenseClassificationEstimate(fixed, variable,
                "Fixed = min(average expenses, recurring EXPENSE monthly-average sum); "
                        + "variable = max(average expenses - fixed, 0)",
                confidence, "Recurring-based estimates require user review and may be corrected before profile update.");
    }

    private FinancialVolatilityPattern calculateVolatility(List<MonthlyCashFlowPattern> months,
                                                            AverageFinancialPattern averages) {
        BigDecimal incomeMad = mad(months.stream().map(MonthlyCashFlowPattern::income).toList(),
                averages.averageMonthlyIncome());
        BigDecimal expenseMad = mad(months.stream().map(MonthlyCashFlowPattern::expenses).toList(),
                averages.averageMonthlyExpenses());
        BigDecimal cashFlowMad = mad(months.stream().map(MonthlyCashFlowPattern::monthlySurplus).toList(),
                averages.averageMonthlySurplus());
        return new FinancialVolatilityPattern(incomeMad, expenseMad, cashFlowMad,
                volatilityRatio(incomeMad, averages.averageMonthlyIncome()),
                volatilityRatio(expenseMad, averages.averageMonthlyExpenses()),
                volatilityRatio(cashFlowMad, averages.averageMonthlySurplus()));
    }

    private SpendingTrendPattern calculateTrend(List<MonthlyCashFlowPattern> months) {
        if (months.size() < 3) {
            return new SpendingTrendPattern(SpendingTrend.INSUFFICIENT_DATA, null, null, null);
        }
        int midpoint = months.size() / 2;
        BigDecimal firstAverage = average(months.subList(0, midpoint).stream()
                .map(MonthlyCashFlowPattern::expenses).toList(), midpoint);
        BigDecimal secondAverage = average(months.subList(midpoint, months.size()).stream()
                .map(MonthlyCashFlowPattern::expenses).toList(), months.size() - midpoint);
        if (firstAverage.signum() == 0) {
            return new SpendingTrendPattern(SpendingTrend.INSUFFICIENT_DATA, null,
                    firstAverage, secondAverage);
        }
        BigDecimal trendRate = percent(secondAverage.subtract(firstAverage), firstAverage);
        SpendingTrend trend = trendRate.compareTo(rules.trendThresholdPercent()) > 0
                ? SpendingTrend.INCREASING
                : trendRate.compareTo(rules.trendThresholdPercent().negate()) < 0
                ? SpendingTrend.DECREASING : SpendingTrend.STABLE;
        return new SpendingTrendPattern(trend, trendRate, firstAverage, secondAverage);
    }

    private FinancialProfileDraft createDraft(YearMonth start, YearMonth end, int monthCount,
                                               AverageFinancialPattern averages,
                                               ExpenseClassificationEstimate expenseEstimate) {
        String sufficiency = monthCount >= rules.highConfidenceMinimumMonths() ? "HIGH"
                : monthCount >= rules.recurringMinimumDistinctMonths() ? "MEDIUM" : "LOW";
        return new FinancialProfileDraft(averages.averageMonthlyIncome(),
                expenseEstimate.estimatedFixedExpenses(), expenseEstimate.estimatedVariableExpenses(),
                averages.averageMonthlySavingTransfers(), averages.averageMonthlyInvestmentTransfers(),
                start, end, sufficiency, List.of(
                "monthlyIncome = average monthly INCOME",
                "fixedExpenses = min(average expenses, recurring EXPENSE monthly-average sum)",
                "variableExpenses = max(average monthly EXPENSE - fixedExpenses, 0)",
                "monthlySavings = average monthly SAVING_TRANSFER",
                "monthlyInvestment = average monthly INVESTMENT_TRANSFER"), true);
    }

    private List<PatternWarning> createWarnings(int monthCount, AverageFinancialPattern averages,
                                                Map<TransactionCategory, BigDecimal> categoryExpenses,
                                                List<RecurringTransactionPattern> recurring,
                                                FinancialVolatilityPattern volatility,
                                                List<YearMonth> deficitMonths) {
        Map<PatternWarningCode, PatternWarning> warnings = new LinkedHashMap<>();
        if (monthCount < rules.minimumHistoryMonths()) {
            add(warnings, PatternWarningCode.INSUFFICIENT_HISTORY,
                    "Less than two months of history is available; review aggregate estimates carefully.");
        }
        if (averages.averageMonthlyIncome().signum() == 0) {
            add(warnings, PatternWarningCode.NO_INCOME_FOUND,
                    "No income transactions were found; savings rate is unavailable.");
        }
        if (averages.averageMonthlyExpenses().signum() == 0) {
            add(warnings, PatternWarningCode.NO_EXPENSE_FOUND, "No expense transactions were found.");
        }
        if (above(volatility.incomeVolatilityRatioPercent(), rules.highVolatilityThresholdPercent())) {
            add(warnings, PatternWarningCode.HIGH_INCOME_VOLATILITY,
                    "Income variability exceeds the configured review threshold.");
        }
        if (above(volatility.expenseVolatilityRatioPercent(), rules.highVolatilityThresholdPercent())) {
            add(warnings, PatternWarningCode.HIGH_EXPENSE_VOLATILITY,
                    "Expense variability exceeds the configured review threshold.");
        }
        if (!deficitMonths.isEmpty()) {
            add(warnings, PatternWarningCode.NEGATIVE_CASH_FLOW_MONTHS,
                    "At least one month has negative cash-flow surplus and should be reviewed.");
        }
        if (averages.savingsRatePercent() != null
                && averages.savingsRatePercent().compareTo(rules.lowSavingsRateThresholdPercent()) < 0) {
            add(warnings, PatternWarningCode.LOW_SAVINGS_RATE,
                    "The calculated savings-rate signal is below the configured review threshold.");
        }
        if (monthCount < rules.recurringMinimumDistinctMonths() || recurring.isEmpty()) {
            add(warnings, PatternWarningCode.RECURRING_PATTERN_UNAVAILABLE,
                    "Recurring transaction patterns could not be established from the available history.");
        }
        BigDecimal totalExpenses = categoryExpenses.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal uncategorized = categoryExpenses.getOrDefault(TransactionCategory.UNCATEGORIZED,
                BigDecimal.ZERO);
        if (totalExpenses.signum() > 0
                && percent(uncategorized, totalExpenses)
                .compareTo(rules.manyUncategorizedThresholdPercent()) > 0) {
            add(warnings, PatternWarningCode.MANY_UNCATEGORIZED_TRANSACTIONS,
                    "Uncategorized expenses exceed the configured review threshold.");
        }
        add(warnings, PatternWarningCode.PROFILE_REVIEW_REQUIRED,
                "Pattern-based profile draft values require explicit user review before any profile update.");
        return List.copyOf(warnings.values());
    }

    private Comparator<NormalizedTransaction> transactionOrder() {
        return Comparator.comparing(NormalizedTransaction::transactionDate)
                .thenComparing(NormalizedTransaction::type)
                .thenComparing(NormalizedTransaction::category)
                .thenComparing(NormalizedTransaction::description)
                .thenComparing(NormalizedTransaction::amount)
                .thenComparing(transaction -> transaction.transactionId() == null ? "" : transaction.transactionId());
    }

    private BigDecimal average(List<BigDecimal> values, int count) {
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(total, BigDecimal.valueOf(count));
    }

    private BigDecimal mad(List<BigDecimal> values, BigDecimal average) {
        BigDecimal deviations = values.stream().map(value -> value.subtract(average).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(deviations, BigDecimal.valueOf(values.size()));
    }

    private BigDecimal volatilityRatio(BigDecimal deviation, BigDecimal average) {
        return average.signum() == 0 ? null : percent(deviation, average.abs());
    }

    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return divide(sorted.get(middle - 1).add(sorted.get(middle)), BigDecimal.valueOf(2));
    }

    private String normalizeDescription(String description) {
        return description.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private String displayDescription(String description) {
        return description.strip().replaceAll("\\s+", " ");
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator.signum() == 0) {
            return BigDecimal.ZERO.setScale(rules.percentageScale(), RoundingMode.HALF_UP);
        }
        return numerator.multiply(ONE_HUNDRED)
                .divide(denominator, rules.percentageScale(), RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return percent(numerator, denominator);
    }

    private BigDecimal ratioValue(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, rules.percentageScale(), RoundingMode.HALF_UP);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, rules.moneyScale(), RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(rules.moneyScale(), RoundingMode.HALF_UP);
    }

    private boolean above(BigDecimal value, BigDecimal threshold) {
        return value != null && value.compareTo(threshold) > 0;
    }

    private void add(Map<PatternWarningCode, PatternWarning> warnings,
                     PatternWarningCode code, String message) {
        warnings.putIfAbsent(code, new PatternWarning(code, message));
    }

    private record RecurringKey(String normalizedDescription, TransactionType type,
                                TransactionCategory category) {
    }

    private final class MonthlyAccumulator {
        private int transactionCount;
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expenses = BigDecimal.ZERO;
        private BigDecimal savingTransfers = BigDecimal.ZERO;
        private BigDecimal investmentTransfers = BigDecimal.ZERO;
        private BigDecimal debtPayments = BigDecimal.ZERO;
        private BigDecimal transfers = BigDecimal.ZERO;

        private void add(NormalizedTransaction transaction) {
            transactionCount++;
            switch (transaction.type()) {
                case INCOME -> income = income.add(transaction.amount());
                case EXPENSE -> expenses = expenses.add(transaction.amount());
                case SAVING_TRANSFER -> savingTransfers = savingTransfers.add(transaction.amount());
                case INVESTMENT_TRANSFER -> investmentTransfers = investmentTransfers.add(transaction.amount());
                case DEBT_PAYMENT -> debtPayments = debtPayments.add(transaction.amount());
                case TRANSFER -> transfers = transfers.add(transaction.amount());
            }
        }

        private MonthlyCashFlowPattern toPattern(YearMonth month) {
            BigDecimal normalizedIncome = money(income);
            BigDecimal normalizedExpenses = money(expenses);
            BigDecimal normalizedDebt = money(debtPayments);
            BigDecimal surplus = money(normalizedIncome.subtract(normalizedExpenses).subtract(normalizedDebt));
            BigDecimal liquidity = money(surplus.subtract(savingTransfers).subtract(investmentTransfers));
            return new MonthlyCashFlowPattern(month, transactionCount, normalizedIncome, normalizedExpenses,
                    money(savingTransfers), money(investmentTransfers), normalizedDebt, money(transfers),
                    surplus, liquidity);
        }
    }
}
