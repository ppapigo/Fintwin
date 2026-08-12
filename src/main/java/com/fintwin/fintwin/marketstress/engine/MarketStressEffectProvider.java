package com.fintwin.fintwin.marketstress.engine;

import com.fintwin.fintwin.marketstress.domain.MarketImpactBreakdown;
import com.fintwin.fintwin.marketstress.domain.MarketStressScenario;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationEffects;
import com.fintwin.fintwin.simulation.engine.MonthlySimulationEffectProvider;

import java.math.BigDecimal;
import java.time.YearMonth;

final class MarketStressEffectProvider implements MonthlySimulationEffectProvider {
    private final MarketStressScenario scenario;
    private final MarketImpactBreakdown impact;

    MarketStressEffectProvider(MarketStressScenario scenario, MarketImpactBreakdown impact) {
        this.scenario = scenario;
        this.impact = impact;
    }

    @Override
    public MonthlySimulationEffects effectsFor(YearMonth yearMonth) {
        boolean shockMonth = yearMonth.equals(scenario.shockYearMonth());
        boolean rateChangeActive = !yearMonth.isBefore(scenario.shockYearMonth());
        return new MonthlySimulationEffects(
                rateChangeActive ? scenario.loanInterestRateChangePercentagePoints() : BigDecimal.ZERO,
                shockMonth ? impact.totalInvestmentImpact() : BigDecimal.ZERO);
    }
}
