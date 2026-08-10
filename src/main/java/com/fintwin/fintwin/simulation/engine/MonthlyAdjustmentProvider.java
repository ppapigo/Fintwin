package com.fintwin.fintwin.simulation.engine;

import com.fintwin.fintwin.simulation.domain.MonthlyAdjustments;

import java.time.YearMonth;

@FunctionalInterface
public interface MonthlyAdjustmentProvider {
    MonthlyAdjustments adjustmentsFor(YearMonth yearMonth);

    static MonthlyAdjustmentProvider none() {
        return ignored -> MonthlyAdjustments.none();
    }
}
