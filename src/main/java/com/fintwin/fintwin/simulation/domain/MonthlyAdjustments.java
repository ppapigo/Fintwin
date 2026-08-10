package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record MonthlyAdjustments(
        BigDecimal incomeDelta,
        BigDecimal expenseDelta,
        BigDecimal investmentContributionDelta,
        BigDecimal oneTimeExpense,
        BigDecimal extraDebtRepayment,
        boolean incomePaused
) {
    private static final MonthlyAdjustments NONE = new MonthlyAdjustments(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);

    public MonthlyAdjustments {
        Objects.requireNonNull(incomeDelta);
        Objects.requireNonNull(expenseDelta);
        Objects.requireNonNull(investmentContributionDelta);
        Objects.requireNonNull(oneTimeExpense);
        Objects.requireNonNull(extraDebtRepayment);
        if (oneTimeExpense.signum() < 0 || extraDebtRepayment.signum() < 0) {
            throw new IllegalArgumentException("One-time expense and extra debt repayment cannot be negative");
        }
    }

    public static MonthlyAdjustments none() {
        return NONE;
    }
}
