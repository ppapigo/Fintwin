package com.fintwin.fintwin.goal.domain;

import java.math.BigDecimal;

public record GoalSolverMetadata(
        BigDecimal searchResolution,
        int maximumIterationsPerPlan,
        BigDecimal incomeSearchUpperLimit,
        int totalIterations,
        String searchAlgorithm,
        String monotonicityBasis
) {
}
