package com.fintwin.fintwin.agent.dto;

import com.fintwin.fintwin.agent.domain.AgentExecutionInput;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public record AgentExecutionRequest(
        @NotBlank @Size(max = 50) String intent,
        YearMonth startYearMonth,
        Integer horizonMonths,
        @Valid BaselineSimulationRequest.Assumptions assumptions,
        @Size(max = 20) List<@NotNull @Valid FinancialEventRequest> events,
        @Size(max = 100) String goalType,
        @DecimalMin(value = "0.00", inclusive = false)
        @Digits(integer = 17, fraction = 2) BigDecimal targetAmount
) {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);

    @AssertTrue(message = "horizonMonths must be one of 12, 36, or 60")
    public boolean isSupportedHorizon() {
        return horizonMonths == null || SUPPORTED_HORIZONS.contains(horizonMonths);
    }

    public AgentExecutionInput toDomain() {
        return new AgentExecutionInput(intent, startYearMonth, horizonMonths, assumptions, events,
                goalType, targetAmount);
    }
}
