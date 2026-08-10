package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;

public record ExpenseClassificationEstimate(
        BigDecimal estimatedFixedExpenses,
        BigDecimal estimatedVariableExpenses,
        String calculationMethod,
        String dataConfidence,
        String reviewNotice
) {
}
