package com.fintwin.fintwin.scenario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = false)
public record MultiScenarioComparisonRequest(
        @NotNull YearMonth startYearMonth,
        @NotNull Integer horizonMonths,
        @Valid @NotNull BaselineSimulationRequest.Assumptions assumptions,
        @NotNull @Size(min = 1, max = 4) List<@NotNull @Valid ScenarioRequest> scenarios
) {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);
    private static final int MAX_TOTAL_EVENTS = 80;

    @AssertTrue(message = "horizonMonths must be one of 12, 36, or 60")
    public boolean isSupportedHorizon() {
        return horizonMonths == null || SUPPORTED_HORIZONS.contains(horizonMonths);
    }

    @AssertTrue(message = "scenarioKey must be unique")
    public boolean hasUniqueScenarioKeys() {
        if (scenarios == null) {
            return true;
        }
        Set<String> keys = new HashSet<>();
        return scenarios.stream().allMatch(scenario -> scenario == null || keys.add(scenario.scenarioKey()));
    }

    @AssertTrue(message = "the request must contain at most 80 events")
    public boolean isWithinTotalEventLimit() {
        if (scenarios == null) {
            return true;
        }
        return scenarios.stream().filter(java.util.Objects::nonNull)
                .map(ScenarioRequest::events).filter(java.util.Objects::nonNull)
                .mapToInt(List::size).sum() <= MAX_TOTAL_EVENTS;
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ScenarioRequest(
            @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_-]+") String scenarioKey,
            @NotBlank @Size(max = 100) String label,
            @NotNull @Size(max = 20) List<@NotNull @Valid FinancialEventRequest> events
    ) {
        @AssertTrue(message = "label must not contain control characters")
        public boolean hasSafeLabel() {
            return label == null || label.codePoints().noneMatch(Character::isISOControl);
        }
    }
}
