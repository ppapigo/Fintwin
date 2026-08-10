package com.fintwin.fintwin.agent.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record GoalAgentToolResult(
        String goalType,
        BigDecimal targetAmount,
        YearMonth startYearMonth,
        YearMonth targetEndYearMonth,
        int horizonMonths,
        String goalStatus,
        BigDecimal baselineFinalNetWorth,
        BigDecimal goalGap,
        YearMonth baselineFirstAchievedYearMonth,
        List<GoalPlanSummary> plans,
        List<String> warningCodes
) implements AgentToolResult {
    public GoalAgentToolResult {
        plans = List.copyOf(plans);
        warningCodes = List.copyOf(warningCodes);
    }

    @Override
    public AgentResultType resultType() {
        return AgentResultType.GOAL_REVERSE_SIMULATION;
    }

    public record GoalPlanSummary(
            String planType,
            String planStatus,
            BigDecimal requiredMonthlyAmount,
            BigDecimal projectedFinalNetWorth,
            BigDecimal goalMargin,
            boolean achieved
    ) {
    }
}
