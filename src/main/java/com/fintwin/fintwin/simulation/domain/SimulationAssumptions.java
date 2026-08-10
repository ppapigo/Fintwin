package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record SimulationAssumptions(
        BigDecimal annualIncomeGrowthRate,
        BigDecimal annualInflationRate,
        BigDecimal annualDepositInterestRate,
        BigDecimal annualInvestmentReturnRate,
        BigDecimal monthlyDebtPayment
) {
    private static final BigDecimal NEGATIVE_ONE_HUNDRED = new BigDecimal("-100");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public SimulationAssumptions {
        Objects.requireNonNull(annualIncomeGrowthRate);
        Objects.requireNonNull(annualInflationRate);
        Objects.requireNonNull(annualDepositInterestRate);
        Objects.requireNonNull(annualInvestmentReturnRate);
        Objects.requireNonNull(monthlyDebtPayment);
        requireRange(annualIncomeGrowthRate, NEGATIVE_ONE_HUNDRED, ONE_HUNDRED, "annualIncomeGrowthRate");
        requireRange(annualInflationRate, NEGATIVE_ONE_HUNDRED, ONE_HUNDRED, "annualInflationRate");
        requireRange(annualDepositInterestRate, BigDecimal.ZERO, ONE_HUNDRED, "annualDepositInterestRate");
        requireRange(annualInvestmentReturnRate, NEGATIVE_ONE_HUNDRED, ONE_HUNDRED,
                "annualInvestmentReturnRate");
        if (monthlyDebtPayment.signum() < 0) {
            throw new IllegalArgumentException("monthlyDebtPayment must be zero or greater");
        }
    }

    private static void requireRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " is outside the supported range");
        }
    }
}
