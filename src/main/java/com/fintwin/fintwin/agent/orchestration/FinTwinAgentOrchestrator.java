package com.fintwin.fintwin.agent.orchestration;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentExecutionInput;
import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentExecutionTrace;
import com.fintwin.fintwin.agent.domain.AgentFailureCode;
import com.fintwin.fintwin.agent.domain.AgentRejectionCode;
import com.fintwin.fintwin.agent.domain.AgentState;
import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.RiskFinding;
import com.fintwin.fintwin.agent.explanation.RuleBasedExplanationComposer;
import com.fintwin.fintwin.agent.gap.InformationGapChecker;
import com.fintwin.fintwin.agent.risk.DeterministicRiskChecker;
import com.fintwin.fintwin.agent.routing.AgentRoute;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.agent.routing.DeterministicIntentRouter;
import com.fintwin.fintwin.agent.tool.BaselineSimulationTool;
import com.fintwin.fintwin.agent.tool.GoalReverseSimulationTool;
import com.fintwin.fintwin.agent.tool.ScenarioComparisonTool;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public final class FinTwinAgentOrchestrator {
    private static final String PRIVACY_NOTICE =
            "Agent 결과와 Financial Profile은 외부 AI Payload로 전달되지 않습니다.";
    private static final String DISCLAIMER =
            "FinTwin Agent는 허용된 결정론적 계산을 조정하며 금융상품을 추천하지 않습니다.";

    private final DeterministicIntentRouter intentRouter;
    private final InformationGapChecker gapChecker;
    private final BaselineSimulationTool baselineTool;
    private final ScenarioComparisonTool scenarioTool;
    private final GoalReverseSimulationTool goalTool;
    private final DeterministicRiskChecker riskChecker;
    private final RuleBasedExplanationComposer explanationComposer;

    public FinTwinAgentOrchestrator(DeterministicIntentRouter intentRouter,
                                    InformationGapChecker gapChecker,
                                    BaselineSimulationTool baselineTool,
                                    ScenarioComparisonTool scenarioTool,
                                    GoalReverseSimulationTool goalTool,
                                    DeterministicRiskChecker riskChecker,
                                    RuleBasedExplanationComposer explanationComposer) {
        this.intentRouter = intentRouter;
        this.gapChecker = gapChecker;
        this.baselineTool = baselineTool;
        this.scenarioTool = scenarioTool;
        this.goalTool = goalTool;
        this.riskChecker = riskChecker;
        this.explanationComposer = explanationComposer;
    }

    public AgentExecutionResult execute(Long currentUserId, AgentExecutionInput input) {
        AgentExecutionTrace trace = AgentExecutionTrace.start();
        AgentRoute route = intentRouter.route(input.requestedIntent()).orElse(null);
        if (route == null) {
            trace.transitionTo(AgentState.REJECTED, "DeterministicIntentRouter", "UNSUPPORTED_INTENT");
            return rejected(input.requestedIntent(), trace);
        }

        trace.transitionTo(AgentState.ROUTED, "DeterministicIntentRouter",
                route.toolName().name() + "_SELECTED");
        AgentCommand command = input.toCommand(route.intent());
        List<MissingInformation> missingInformation = gapChecker.check(currentUserId, command);
        trace.transitionTo(AgentState.GAP_CHECKED, "InformationGapChecker",
                missingInformation.isEmpty() ? "REQUIRED_INFORMATION_COMPLETE" : "REQUIRED_INFORMATION_MISSING");
        if (!missingInformation.isEmpty()) {
            trace.transitionTo(AgentState.NEEDS_INPUT, "FinTwinAgentOrchestrator", "CLARIFICATION_REQUIRED");
            return needsInput(input.requestedIntent(), route.toolName(), missingInformation, trace);
        }

        trace.transitionTo(AgentState.TOOL_EXECUTING, route.toolName().name(), "PRIMARY_TOOL_STARTED");
        AgentToolResult toolResult;
        try {
            toolResult = executeAllowedTool(route.toolName(), currentUserId, command);
        } catch (InvalidRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            trace.transitionTo(AgentState.FAILED, route.toolName().name(), "TOOL_EXECUTION_FAILED");
            return failed(input.requestedIntent(), route.toolName(), trace);
        }
        trace.transitionTo(AgentState.TOOL_COMPLETED, route.toolName().name(), "PRIMARY_TOOL_COMPLETED");

        List<RiskFinding> risks = riskChecker.check(toolResult);
        trace.transitionTo(AgentState.RISK_CHECKED, "DeterministicRiskChecker", "RISK_RULES_APPLIED");
        var explanation = explanationComposer.compose(toolResult);
        trace.transitionTo(AgentState.EXPLAINED, "RuleBasedExplanationComposer", "EXPLANATION_COMPOSED");
        trace.transitionTo(AgentState.COMPLETED, "FinTwinAgentOrchestrator", "EXECUTION_COMPLETED");
        return new AgentExecutionResult(AgentStatus.COMPLETED, input.requestedIntent(), route.toolName(),
                null, null, List.of(), List.of(), toolResult.resultType(), toolResult, risks, explanation,
                trace.steps(), 1, PRIVACY_NOTICE, DISCLAIMER);
    }

    private AgentToolResult executeAllowedTool(AgentToolName toolName, Long currentUserId, AgentCommand command) {
        return switch (toolName) {
            case BASELINE_SIMULATION_TOOL -> baselineTool.execute(currentUserId, command);
            case SCENARIO_COMPARISON_TOOL -> scenarioTool.execute(currentUserId, command);
            case GOAL_REVERSE_SIMULATION_TOOL -> goalTool.execute(currentUserId, command);
        };
    }

    private AgentExecutionResult rejected(String intent, AgentExecutionTrace trace) {
        return new AgentExecutionResult(AgentStatus.REJECTED, intent, null,
                AgentRejectionCode.UNSUPPORTED_INTENT, null, List.of(), List.of(), null, null, List.of(), null,
                trace.steps(), 0, PRIVACY_NOTICE, DISCLAIMER);
    }

    private AgentExecutionResult needsInput(String intent, AgentToolName toolName,
                                            List<MissingInformation> missingInformation,
                                            AgentExecutionTrace trace) {
        return new AgentExecutionResult(AgentStatus.NEEDS_INPUT, intent, toolName, null, null,
                missingInformation, missingInformation.stream().map(MissingInformation::question).toList(),
                null, null, List.of(), null, trace.steps(), 0, PRIVACY_NOTICE, DISCLAIMER);
    }

    private AgentExecutionResult failed(String intent, AgentToolName toolName, AgentExecutionTrace trace) {
        return new AgentExecutionResult(AgentStatus.FAILED, intent, toolName, null,
                AgentFailureCode.TOOL_EXECUTION_FAILED, List.of(), List.of(), null, null, List.of(), null,
                trace.steps(), 1, PRIVACY_NOTICE, DISCLAIMER);
    }
}
