package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record FinancialProfileDraft(
        BigDecimal monthlyIncome,
        BigDecimal fixedExpenses,
        BigDecimal variableExpenses,
        BigDecimal monthlySavings,
        BigDecimal monthlyInvestment,
        YearMonth analysisStartYearMonth,
        YearMonth analysisEndYearMonth,
        String dataSufficiency,
        List<String> calculationBasis,
        boolean userConfirmationRequired
) {
    public FinancialProfileDraft {
        calculationBasis = List.copyOf(calculationBasis);
    }
}
