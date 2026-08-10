package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;

public record CategorySpendingPattern(
        TransactionCategory category,
        BigDecimal totalExpenses,
        BigDecimal averageMonthlyExpenses,
        BigDecimal spendingRatioPercent
) {
}
