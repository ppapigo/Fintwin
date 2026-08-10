package com.fintwin.fintwin.agent.tool;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import com.fintwin.fintwin.simulation.service.BaselineSimulationService;
import org.springframework.stereotype.Component;

@Component
public final class BaselineSimulationTool implements FinTwinAgentTool {
    private final BaselineSimulationService simulationService;

    public BaselineSimulationTool(BaselineSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @Override
    public AgentToolName name() {
        return AgentToolName.BASELINE_SIMULATION_TOOL;
    }

    @Override
    public AgentToolResult execute(Long currentUserId, AgentCommand command) {
        BaselineSimulationResponse response = simulationService.simulate(currentUserId,
                new BaselineSimulationRequest(command.startYearMonth(), command.horizonMonths(),
                        command.assumptions()));
        BaselineSimulationResponse.MonthlyResultResponse last = response.monthlyResults().getLast();
        return new BaselineAgentToolResult(response.startYearMonth(), response.horizonMonths(), last.yearMonth(),
                last.liquidAssets(), last.investmentAssets(), last.remainingDebt(), last.netWorth(),
                response.monthlyResults().stream().filter(BaselineSimulationResponse.MonthlyResultResponse::cashShortfall)
                        .map(BaselineSimulationResponse.MonthlyResultResponse::yearMonth).toList(),
                response.monthlyResults().stream()
                        .filter(BaselineSimulationResponse.MonthlyResultResponse::negativeAmortization)
                        .map(BaselineSimulationResponse.MonthlyResultResponse::yearMonth).toList());
    }
}
