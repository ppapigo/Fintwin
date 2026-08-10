package com.fintwin.fintwin.agent.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ScenarioAgentToolResult(
        YearMonth startYearMonth,
        int horizonMonths,
        YearMonth finalYearMonth,
        BigDecimal baselineFinalNetWorth,
        BigDecimal whatIfFinalNetWorth,
        BigDecimal netWorthDelta,
        BigDecimal liquidAssetsDelta,
        BigDecimal debtDelta,
        BigDecimal cumulativeIncomeDelta,
        BigDecimal cumulativeConsumptionDelta,
        List<YearMonth> cashShortfallMonths,
        List<YearMonth> negativeAmortizationMonths,
        List<String> serviceWarnings,
        ScenarioComparisonDetails comparisonDetails
) implements AgentToolResult {
    public ScenarioAgentToolResult {
        cashShortfallMonths = List.copyOf(cashShortfallMonths);
        negativeAmortizationMonths = List.copyOf(negativeAmortizationMonths);
        serviceWarnings = List.copyOf(serviceWarnings);
        java.util.Objects.requireNonNull(comparisonDetails);
    }

    @Override
    public AgentResultType resultType() {
        return AgentResultType.SCENARIO_COMPARISON;
    }
}
