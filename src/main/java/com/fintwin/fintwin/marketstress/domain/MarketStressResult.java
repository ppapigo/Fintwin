package com.fintwin.fintwin.marketstress.domain;

import com.fintwin.fintwin.simulation.domain.SimulationSummary;

import java.util.List;

public record MarketStressResult(
        SimulationSummary baseline,
        SimulationSummary stressed,
        MarketImpactBreakdown marketImpactBreakdown,
        RiskComparison riskComparison,
        GoalMarginComparison goalMarginComparison,
        List<MarketStressWarning> warnings
) {
    public MarketStressResult {
        warnings = List.copyOf(warnings);
    }
}
