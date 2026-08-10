package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;

public record FinancialVolatilityPattern(
        BigDecimal incomeMeanAbsoluteDeviation,
        BigDecimal expenseMeanAbsoluteDeviation,
        BigDecimal cashFlowMeanAbsoluteDeviation,
        BigDecimal incomeVolatilityRatioPercent,
        BigDecimal expenseVolatilityRatioPercent,
        BigDecimal cashFlowVolatilityRatioPercent
) {
}
