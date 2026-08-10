package com.fintwin.fintwin.agent.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record BaselineAgentToolResult(
        YearMonth startYearMonth,
        int horizonMonths,
        YearMonth finalYearMonth,
        BigDecimal finalLiquidAssets,
        BigDecimal finalInvestmentAssets,
        BigDecimal finalRemainingDebt,
        BigDecimal finalNetWorth,
        List<YearMonth> cashShortfallMonths,
        List<YearMonth> negativeAmortizationMonths
) implements AgentToolResult {
    public BaselineAgentToolResult {
        cashShortfallMonths = List.copyOf(cashShortfallMonths);
        negativeAmortizationMonths = List.copyOf(negativeAmortizationMonths);
    }

    @Override
    public AgentResultType resultType() {
        return AgentResultType.BASELINE;
    }
}
