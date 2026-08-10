package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record SimulationInput(
        BigDecimal initialLiquidAssets,
        BigDecimal initialInvestmentAssets,
        BigDecimal initialDebt,
        BigDecimal annualDebtInterestRate,
        BigDecimal monthlyIncome,
        BigDecimal monthlyFixedExpenses,
        BigDecimal monthlyVariableExpenses,
        BigDecimal plannedMonthlySavings,
        BigDecimal plannedMonthlyInvestment
) {
    public SimulationInput {
        Objects.requireNonNull(initialLiquidAssets);
        Objects.requireNonNull(initialInvestmentAssets);
        Objects.requireNonNull(initialDebt);
        Objects.requireNonNull(annualDebtInterestRate);
        Objects.requireNonNull(monthlyIncome);
        Objects.requireNonNull(monthlyFixedExpenses);
        Objects.requireNonNull(monthlyVariableExpenses);
        Objects.requireNonNull(plannedMonthlySavings);
        Objects.requireNonNull(plannedMonthlyInvestment);
    }
}
