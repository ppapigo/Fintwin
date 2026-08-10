package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlySimulationResult(
        int monthNumber,
        YearMonth yearMonth,
        BigDecimal income,
        BigDecimal fixedExpenses,
        BigDecimal variableExpenses,
        BigDecimal oneTimeExpense,
        BigDecimal debtInterest,
        BigDecimal debtPayment,
        BigDecimal extraDebtRepayment,
        BigDecimal principalRepaid,
        BigDecimal savingsAllocation,
        BigDecimal investmentContribution,
        BigDecimal depositInterest,
        BigDecimal investmentReturn,
        BigDecimal disposableCashFlow,
        BigDecimal liquidAssets,
        BigDecimal investmentAssets,
        BigDecimal totalFinancialAssets,
        BigDecimal remainingDebt,
        BigDecimal netWorth,
        boolean cashShortfall,
        boolean negativeAmortization,
        CumulativeTotals cumulativeTotals
) {
}
