package com.fintwin.fintwin.goal.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

public record FinancialGoal(
        GoalType goalType,
        BigDecimal targetAmount,
        YearMonth startYearMonth,
        int horizonMonths
) {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);

    public FinancialGoal {
        Objects.requireNonNull(goalType);
        Objects.requireNonNull(targetAmount);
        Objects.requireNonNull(startYearMonth);
        if (targetAmount.signum() <= 0) {
            throw new IllegalArgumentException("targetAmount must be greater than zero");
        }
        if (!SUPPORTED_HORIZONS.contains(horizonMonths)) {
            throw new IllegalArgumentException("horizonMonths must be one of 12, 36, or 60");
        }
    }

    public YearMonth targetEndYearMonth() {
        return startYearMonth.plusMonths(horizonMonths - 1L);
    }
}
