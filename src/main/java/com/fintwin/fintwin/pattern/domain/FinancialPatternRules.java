package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;

public record FinancialPatternRules(
        String algorithmVersion,
        long maximumFileBytes,
        int maximumTransactionRows,
        int maximumAnalysisMonths,
        int minimumHistoryMonths,
        int recurringMinimumDistinctMonths,
        int highConfidenceMinimumMonths,
        BigDecimal recurringAmountTolerancePercent,
        BigDecimal trendThresholdPercent,
        BigDecimal highVolatilityThresholdPercent,
        BigDecimal lowSavingsRateThresholdPercent,
        BigDecimal manyUncategorizedThresholdPercent,
        int moneyScale,
        int percentageScale,
        String roundingMode
) {
    public static FinancialPatternRules standard() {
        return new FinancialPatternRules("fintwin-pattern-v1", 2L * 1024L * 1024L, 10_000, 60, 2, 3, 6,
                new BigDecimal("10.0000"), new BigDecimal("5.0000"), new BigDecimal("30.0000"),
                new BigDecimal("10.0000"), new BigDecimal("20.0000"), 2, 4, "HALF_UP");
    }
}
