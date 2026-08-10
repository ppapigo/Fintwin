package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record SimulationCheckpoint(
        int monthNumber,
        YearMonth yearMonth,
        BigDecimal liquidAssets,
        BigDecimal investmentAssets,
        BigDecimal totalFinancialAssets,
        BigDecimal remainingDebt,
        BigDecimal netWorth,
        CumulativeTotals cumulativeTotals
) {
    public static SimulationCheckpoint from(MonthlySimulationResult result) {
        return new SimulationCheckpoint(result.monthNumber(), result.yearMonth(), result.liquidAssets(),
                result.investmentAssets(), result.totalFinancialAssets(), result.remainingDebt(), result.netWorth(),
                result.cumulativeTotals());
    }
}
