package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Explicit, request-scoped extension effects for deterministic monthly simulations.
 */
public record MonthlySimulationEffects(
        BigDecimal annualDebtInterestRateDelta,
        BigDecimal investmentAssetValueAdjustment
) {
    private static final MonthlySimulationEffects NONE = new MonthlySimulationEffects(
            BigDecimal.ZERO, BigDecimal.ZERO);

    public MonthlySimulationEffects {
        Objects.requireNonNull(annualDebtInterestRateDelta);
        Objects.requireNonNull(investmentAssetValueAdjustment);
    }

    public static MonthlySimulationEffects none() {
        return NONE;
    }
}
