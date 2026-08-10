package com.fintwin.fintwin.agent.tool;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.GoalAgentToolResult;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationRequest;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationResponse;
import com.fintwin.fintwin.goal.service.GoalReverseSimulationService;
import org.springframework.stereotype.Component;

@Component
public final class GoalReverseSimulationTool implements FinTwinAgentTool {
    private final GoalReverseSimulationService goalReverseSimulationService;

    public GoalReverseSimulationTool(GoalReverseSimulationService goalReverseSimulationService) {
        this.goalReverseSimulationService = goalReverseSimulationService;
    }

    @Override
    public AgentToolName name() {
        return AgentToolName.GOAL_REVERSE_SIMULATION_TOOL;
    }

    @Override
    public AgentToolResult execute(Long currentUserId, AgentCommand command) {
        GoalReverseSimulationResponse response = goalReverseSimulationService.reverseSimulate(currentUserId,
                new GoalReverseSimulationRequest(command.goalType(), command.targetAmount(),
                        command.startYearMonth(), command.horizonMonths(), command.assumptions()));
        return new GoalAgentToolResult(response.goalType(), response.targetAmount(), response.startYearMonth(),
                response.targetEndYearMonth(), response.horizonMonths(), response.goalStatus(),
                response.baselineFinalNetWorth(), response.goalGap(), response.baselineFirstAchievedYearMonth(),
                response.plans().stream().map(plan -> new GoalAgentToolResult.GoalPlanSummary(
                        plan.planType(), plan.planStatus(), plan.requiredMonthlyAmount(),
                        plan.projectedFinalNetWorth(), plan.goalMargin(), plan.achieved())).toList(),
                response.warnings().stream().map(GoalReverseSimulationResponse.GoalWarningResponse::code).toList());
    }
}
