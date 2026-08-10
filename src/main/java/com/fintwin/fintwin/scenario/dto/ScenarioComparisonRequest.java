package com.fintwin.fintwin.scenario.dto;

import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public record ScenarioComparisonRequest(
        @NotBlank @Size(max = 100) String scenarioName,
        @NotNull YearMonth startYearMonth,
        @NotNull Integer horizonMonths,
        @Valid @NotNull BaselineSimulationRequest.Assumptions assumptions,
        @NotNull @Size(min = 1, max = 20) List<@Valid FinancialEventRequest> events
) {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);

    @AssertTrue(message = "horizonMonths must be one of 12, 36, or 60")
    public boolean isSupportedHorizon() {
        return horizonMonths == null || SUPPORTED_HORIZONS.contains(horizonMonths);
    }
}
