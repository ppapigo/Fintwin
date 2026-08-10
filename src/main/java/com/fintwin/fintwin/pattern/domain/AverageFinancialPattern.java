package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record AverageFinancialPattern(
        BigDecimal averageMonthlyIncome,
        BigDecimal averageMonthlyExpenses,
        BigDecimal averageMonthlySavingTransfers,
        BigDecimal averageMonthlyInvestmentTransfers,
        BigDecimal averageMonthlyDebtPayments,
        BigDecimal averageMonthlySurplus,
        BigDecimal averageMonthlyLiquidityAfterAllocations,
        BigDecimal savingsRatePercent,
        YearMonth highestExpenseMonth,
        BigDecimal highestExpenseAmount,
        YearMonth lowestExpenseMonth,
        BigDecimal lowestExpenseAmount,
        int deficitMonthCount,
        BigDecimal deficitMonthRatioPercent
) {
}
