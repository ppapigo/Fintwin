package com.fintwin.fintwin.financialprofile.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record FinancialProfileCreateRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyIncome,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal cashAssets,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal deposits,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal investmentAssets,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal totalLoanBalance,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 3, fraction = 4) BigDecimal loanInterestRate,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyFixedExpenses,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyVariableExpenses,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlySavings,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyInvestments
) {
}
