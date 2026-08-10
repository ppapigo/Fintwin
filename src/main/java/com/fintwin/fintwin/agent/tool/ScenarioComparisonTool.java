package com.fintwin.fintwin.agent.tool;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.service.ScenarioSimulationService;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import org.springframework.stereotype.Component;

@Component
public final class ScenarioComparisonTool implements FinTwinAgentTool {
    private static final String AGENT_SCENARIO_NAME = "FinTwin Agent What-if";

    private final ScenarioSimulationService scenarioSimulationService;

    public ScenarioComparisonTool(ScenarioSimulationService scenarioSimulationService) {
        this.scenarioSimulationService = scenarioSimulationService;
    }

    @Override
    public AgentToolName name() {
        return AgentToolName.SCENARIO_COMPARISON_TOOL;
    }

    @Override
    public AgentToolResult execute(Long currentUserId, AgentCommand command) {
        ScenarioComparisonResponse response = scenarioSimulationService.compare(currentUserId,
                new ScenarioComparisonRequest(AGENT_SCENARIO_NAME, command.startYearMonth(),
                        command.horizonMonths(), command.assumptions(), command.events()));
        BaselineSimulationResponse.MonthlyResultResponse baselineLast = response.baseline().monthlyResults().getLast();
        BaselineSimulationResponse.MonthlyResultResponse whatIfLast = response.whatIf().monthlyResults().getLast();
        ScenarioComparisonResponse.ComparisonResult comparison = response.finalComparison();
        return new ScenarioAgentToolResult(response.startYearMonth(), response.horizonMonths(),
                whatIfLast.yearMonth(), baselineLast.netWorth(), whatIfLast.netWorth(), comparison.netWorthDelta(),
                comparison.liquidAssetsDelta(), comparison.debtDelta(), comparison.cumulativeIncomeDelta(),
                comparison.cumulativeConsumptionDelta(),
                response.whatIf().monthlyResults().stream()
                        .filter(BaselineSimulationResponse.MonthlyResultResponse::cashShortfall)
                        .map(BaselineSimulationResponse.MonthlyResultResponse::yearMonth).toList(),
                response.whatIf().monthlyResults().stream()
                        .filter(BaselineSimulationResponse.MonthlyResultResponse::negativeAmortization)
                        .map(BaselineSimulationResponse.MonthlyResultResponse::yearMonth).toList(),
                response.warnings());
    }
}
