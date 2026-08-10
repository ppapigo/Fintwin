package com.fintwin.fintwin.goal.domain;

import com.fintwin.fintwin.simulation.domain.SimulationSummary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record GoalSolverResult(
        GoalStatus goalStatus,
        BigDecimal currentNetWorth,
        BigDecimal baselineFinalNetWorth,
        BigDecimal goalGap,
        YearMonth baselineFirstAchievedYearMonth,
        SimulationSummary baselineSummary,
        List<GoalPlan> plans,
        GoalSolverMetadata solverMetadata,
        List<GoalWarning> warnings
) {
    public GoalSolverResult {
        Objects.requireNonNull(goalStatus);
        Objects.requireNonNull(currentNetWorth);
        Objects.requireNonNull(baselineFinalNetWorth);
        Objects.requireNonNull(goalGap);
        Objects.requireNonNull(baselineSummary);
        Objects.requireNonNull(solverMetadata);
        plans = List.copyOf(plans);
        warnings = List.copyOf(warnings);
    }
}
