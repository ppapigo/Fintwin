package com.fintwin.fintwin.simulation.domain;

import java.util.List;

public record SimulationSummary(
        List<MonthlySimulationResult> monthlyResults,
        List<SimulationCheckpoint> checkpoints,
        CumulativeTotals finalCumulativeTotals
) {
    public SimulationSummary {
        monthlyResults = List.copyOf(monthlyResults);
        checkpoints = List.copyOf(checkpoints);
    }
}
