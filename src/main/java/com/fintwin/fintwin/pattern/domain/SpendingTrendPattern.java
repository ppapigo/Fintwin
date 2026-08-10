package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;

public record SpendingTrendPattern(
        SpendingTrend trend,
        BigDecimal trendRatePercent,
        BigDecimal firstPeriodAverageExpenses,
        BigDecimal secondPeriodAverageExpenses
) {
}
