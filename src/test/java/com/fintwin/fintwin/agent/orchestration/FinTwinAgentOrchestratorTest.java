package com.fintwin.fintwin.agent.orchestration;

import com.fintwin.fintwin.agent.domain.AgentExecutionInput;
import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.agent.domain.AgentState;
import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.AgentTraceStep;
import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.MissingInformationCode;
import com.fintwin.fintwin.agent.explanation.RuleBasedExplanationComposer;
import com.fintwin.fintwin.agent.gap.InformationGapChecker;
import com.fintwin.fintwin.agent.risk.DeterministicRiskChecker;
import com.fintwin.fintwin.agent.routing.DeterministicIntentRouter;
import com.fintwin.fintwin.agent.tool.BaselineSimulationTool;
import com.fintwin.fintwin.agent.tool.GoalReverseSimulationTool;
import com.fintwin.fintwin.agent.tool.ScenarioComparisonTool;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinTwinAgentOrchestratorTest {
    private final InformationGapChecker gapChecker = mock(InformationGapChecker.class);
    private final BaselineSimulationTool baselineTool = mock(BaselineSimulationTool.class);
    private final ScenarioComparisonTool scenarioTool = mock(ScenarioComparisonTool.class);
    private final GoalReverseSimulationTool goalTool = mock(GoalReverseSimulationTool.class);
    private final FinTwinAgentOrchestrator orchestrator = new FinTwinAgentOrchestrator(
            new DeterministicIntentRouter(), gapChecker, baselineTool, scenarioTool, goalTool,
            new DeterministicRiskChecker(), new RuleBasedExplanationComposer());

    @BeforeEach
    void completeInformationByDefault() {
        when(gapChecker.check(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void completesNormalPathWithExactlyOnePrimaryToolCall() {
        BaselineAgentToolResult toolResult = baselineResult();
        when(baselineTool.execute(anyLong(), any())).thenReturn(toolResult);

        AgentExecutionResult result = orchestrator.execute(7L, baselineInput());

        assertThat(result.status()).isEqualTo(AgentStatus.COMPLETED);
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.typedResult()).isSameAs(toolResult);
        assertThat(result.trace()).extracting(AgentTraceStep::state).containsExactly(
                AgentState.RECEIVED, AgentState.ROUTED, AgentState.GAP_CHECKED, AgentState.TOOL_EXECUTING,
                AgentState.TOOL_COMPLETED, AgentState.RISK_CHECKED, AgentState.EXPLAINED, AgentState.COMPLETED);
        verify(baselineTool, times(1)).execute(anyLong(), any());
        verifyNoInteractions(scenarioTool, goalTool);
    }

    @Test
    void missingInformationStopsBeforeEveryTool() {
        MissingInformation missing = new MissingInformation(MissingInformationCode.HORIZON_REQUIRED,
                "horizonMonths", "시뮬레이션 기간을 입력해주세요.", AgentIntent.BASELINE_SIMULATION);
        when(gapChecker.check(anyLong(), any())).thenReturn(List.of(missing));

        AgentExecutionResult result = orchestrator.execute(7L, baselineInput());

        assertThat(result.status()).isEqualTo(AgentStatus.NEEDS_INPUT);
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.typedResult()).isNull();
        assertThat(result.clarificationQuestions()).containsExactly(missing.question());
        assertThat(result.trace().getLast().state()).isEqualTo(AgentState.NEEDS_INPUT);
        verifyNoInteractions(baselineTool, scenarioTool, goalTool);
    }

    @Test
    void unsupportedIntentIsRejectedWithoutToolSelection() {
        AgentExecutionInput unsupported = new AgentExecutionInput("INVESTMENT_ADVICE", null, null,
                null, null, null, null);

        AgentExecutionResult result = orchestrator.execute(7L, unsupported);

        assertThat(result.status()).isEqualTo(AgentStatus.REJECTED);
        assertThat(result.selectedTool()).isNull();
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.typedResult()).isNull();
        assertThat(result.trace()).extracting(AgentTraceStep::state)
                .containsExactly(AgentState.RECEIVED, AgentState.REJECTED);
        verify(gapChecker, never()).check(anyLong(), any());
        verifyNoInteractions(baselineTool, scenarioTool, goalTool);
    }

    @Test
    void toolFailureReturnsGeneralizedFailedResponseWithoutRetry() {
        when(baselineTool.execute(anyLong(), any())).thenThrow(new IllegalStateException("sensitive detail"));

        AgentExecutionResult result = orchestrator.execute(7L, baselineInput());

        assertThat(result.status()).isEqualTo(AgentStatus.FAILED);
        assertThat(result.failureCode().name()).isEqualTo("TOOL_EXECUTION_FAILED");
        assertThat(result.toString()).doesNotContain("sensitive detail");
        assertThat(result.trace().getLast().state()).isEqualTo(AgentState.FAILED);
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.typedResult()).isNull();
        verify(baselineTool, times(1)).execute(anyLong(), any());
        verifyNoInteractions(scenarioTool, goalTool);
    }

    private AgentExecutionInput baselineInput() {
        return new AgentExecutionInput("BASELINE_SIMULATION", YearMonth.of(2026, 8), 12,
                new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), null, null, null);
    }

    private BaselineAgentToolResult baselineResult() {
        return new BaselineAgentToolResult(YearMonth.of(2026, 8), 12, YearMonth.of(2027, 7),
                new BigDecimal("100"), new BigDecimal("200"), BigDecimal.ZERO,
                new BigDecimal("300"), List.of(), List.of());
    }
}
