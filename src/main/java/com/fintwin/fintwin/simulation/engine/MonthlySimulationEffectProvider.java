package com.fintwin.fintwin.simulation.engine;

import com.fintwin.fintwin.simulation.domain.MonthlySimulationEffects;

import java.time.YearMonth;

@FunctionalInterface
public interface MonthlySimulationEffectProvider {
    MonthlySimulationEffects effectsFor(YearMonth yearMonth);

    static MonthlySimulationEffectProvider none() {
        return ignored -> MonthlySimulationEffects.none();
    }
}
