package com.fintwin.fintwin.goal.domain;

import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record GoalPlan(
        GoalPlanType planType,
        GoalPlanStatus planStatus,
        BigDecimal requiredMonthlyAmount,
        BigDecimal maximumMonthlyAmountTested,
        List<FinancialEvent> events,
        SimulationSummary projectedSummary,
        BigDecimal projectedFinalNetWorth,
        BigDecimal goalMargin,
        YearMonth firstAchievedYearMonth,
        boolean achieved,
        int solverIterations,
        List<String> appliedConstraints,
        List<GoalWarning> warnings
) {
    public GoalPlan {
        Objects.requireNonNull(planType);
        Objects.requireNonNull(planStatus);
        Objects.requireNonNull(maximumMonthlyAmountTested);
        Objects.requireNonNull(projectedSummary);
        Objects.requireNonNull(projectedFinalNetWorth);
        Objects.requireNonNull(goalMargin);
        events = List.copyOf(events);
        appliedConstraints = List.copyOf(appliedConstraints);
        warnings = List.copyOf(warnings);
    }
}
