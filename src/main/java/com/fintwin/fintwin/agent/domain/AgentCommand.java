package com.fintwin.fintwin.agent.domain;

import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

public record AgentCommand(
        AgentIntent intent,
        YearMonth startYearMonth,
        Integer horizonMonths,
        BaselineSimulationRequest.Assumptions assumptions,
        List<FinancialEventRequest> events,
        String goalType,
        BigDecimal targetAmount
) {
    public AgentCommand {
        Objects.requireNonNull(intent);
        events = events == null ? null : List.copyOf(events);
    }
}
