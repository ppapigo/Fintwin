package com.fintwin.fintwin.agent.tool;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.domain.GoalAgentToolResult;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationResponse;
import com.fintwin.fintwin.goal.service.GoalReverseSimulationService;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.service.ScenarioSimulationService;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import com.fintwin.fintwin.simulation.service.BaselineSimulationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentToolsTest {
    @Test
    void baselineToolCallsExistingServiceExactlyOnceAndRemovesProfileIdentifiers() {
        BaselineSimulationService service = mock(BaselineSimulationService.class);
        BaselineSimulationResponse response = mock(BaselineSimulationResponse.class);
        BaselineSimulationResponse.MonthlyResultResponse month = month(YearMonth.of(2026, 8), true, true,
                "100", "200", "50", "250");
        when(response.startYearMonth()).thenReturn(YearMonth.of(2026, 8));
        when(response.horizonMonths()).thenReturn(12);
        when(response.monthlyResults()).thenReturn(List.of(month));
        when(service.simulate(any(), any())).thenReturn(response);

        BaselineAgentToolResult result = (BaselineAgentToolResult) new BaselineSimulationTool(service)
                .execute(7L, command(AgentIntent.BASELINE_SIMULATION, null, null, null));

        verify(service, times(1)).simulate(any(), any());
        assertThat(result.finalNetWorth()).isEqualByComparingTo("250");
        assertThat(result.cashShortfallMonths()).containsExactly(YearMonth.of(2026, 8));
        assertThat(BaselineAgentToolResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("financialProfileId", "financialProfileVersion", "userId");
    }

    @Test
    void scenarioToolCallsComparisonServiceExactlyOnce() {
        ScenarioSimulationService service = mock(ScenarioSimulationService.class);
        ScenarioComparisonResponse response = mock(ScenarioComparisonResponse.class);
        BaselineSimulationResponse baseline = mock(BaselineSimulationResponse.class);
        BaselineSimulationResponse whatIf = mock(BaselineSimulationResponse.class);
        ScenarioComparisonResponse.ComparisonResult comparison = mock(
                ScenarioComparisonResponse.ComparisonResult.class);
        BaselineSimulationResponse.MonthlyResultResponse baselineMonth = month(
                YearMonth.of(2029, 7), false, false, "100", "200", "0", "300");
        BaselineSimulationResponse.MonthlyResultResponse whatIfMonth = month(
                YearMonth.of(2029, 7), false, false, "80", "200", "0", "280");
        when(response.startYearMonth()).thenReturn(YearMonth.of(2026, 8));
        when(response.horizonMonths()).thenReturn(36);
        when(response.baseline()).thenReturn(baseline);
        when(response.whatIf()).thenReturn(whatIf);
        when(response.finalComparison()).thenReturn(comparison);
        when(response.warnings()).thenReturn(List.of());
        when(baseline.monthlyResults()).thenReturn(List.of(baselineMonth));
        when(whatIf.monthlyResults()).thenReturn(List.of(whatIfMonth));
        when(comparison.netWorthDelta()).thenReturn(new BigDecimal("-20"));
        when(comparison.liquidAssetsDelta()).thenReturn(new BigDecimal("-20"));
        when(comparison.debtDelta()).thenReturn(BigDecimal.ZERO);
        when(comparison.cumulativeIncomeDelta()).thenReturn(BigDecimal.ZERO);
        when(comparison.cumulativeConsumptionDelta()).thenReturn(new BigDecimal("20"));
        when(service.compare(any(), any())).thenReturn(response);

        ScenarioAgentToolResult result = (ScenarioAgentToolResult) new ScenarioComparisonTool(service)
                .execute(7L, command(AgentIntent.WHAT_IF_SIMULATION, List.of(event()), null, null));

        verify(service, times(1)).compare(any(), any());
        assertThat(result.netWorthDelta()).isEqualByComparingTo("-20");
    }

    @Test
    void goalToolCallsReverseSimulationServiceExactlyOnce() {
        GoalReverseSimulationService service = mock(GoalReverseSimulationService.class);
        GoalReverseSimulationResponse response = mock(GoalReverseSimulationResponse.class);
        GoalReverseSimulationResponse.GoalPlanResponse plan = mock(
                GoalReverseSimulationResponse.GoalPlanResponse.class);
        GoalReverseSimulationResponse.GoalWarningResponse warning =
                new GoalReverseSimulationResponse.GoalWarningResponse("EXPENSE_REDUCTION_INFEASIBLE", "hidden");
        when(response.goalType()).thenReturn("TARGET_NET_WORTH");
        when(response.targetAmount()).thenReturn(new BigDecimal("50000000"));
        when(response.startYearMonth()).thenReturn(YearMonth.of(2026, 8));
        when(response.targetEndYearMonth()).thenReturn(YearMonth.of(2029, 7));
        when(response.horizonMonths()).thenReturn(36);
        when(response.goalStatus()).thenReturn("PARTIALLY_ACHIEVABLE");
        when(response.baselineFinalNetWorth()).thenReturn(new BigDecimal("30000000"));
        when(response.goalGap()).thenReturn(new BigDecimal("20000000"));
        when(response.plans()).thenReturn(List.of(plan));
        when(response.warnings()).thenReturn(List.of(warning));
        when(plan.planType()).thenReturn("INCREASE_INCOME");
        when(plan.planStatus()).thenReturn("ACHIEVABLE");
        when(plan.requiredMonthlyAmount()).thenReturn(new BigDecimal("500000"));
        when(plan.projectedFinalNetWorth()).thenReturn(new BigDecimal("50000001"));
        when(plan.goalMargin()).thenReturn(BigDecimal.ONE);
        when(plan.achieved()).thenReturn(true);
        when(service.reverseSimulate(any(), any())).thenReturn(response);

        GoalAgentToolResult result = (GoalAgentToolResult) new GoalReverseSimulationTool(service)
                .execute(7L, command(AgentIntent.GOAL_REVERSE_SIMULATION, null,
                        "TARGET_NET_WORTH", new BigDecimal("50000000")));

        verify(service, times(1)).reverseSimulate(any(), any());
        assertThat(result.warningCodes()).containsExactly("EXPENSE_REDUCTION_INFEASIBLE");
        assertThat(result.plans()).hasSize(1);
    }

    private AgentCommand command(AgentIntent intent, List<FinancialEventRequest> events,
                                 String goalType, BigDecimal targetAmount) {
        return new AgentCommand(intent, YearMonth.of(2026, 8), intent == AgentIntent.BASELINE_SIMULATION ? 12 : 36,
                new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), events, goalType, targetAmount);
    }

    private FinancialEventRequest event() {
        return new FinancialEventRequest("event-1", "ONE_TIME_EXPENSE", YearMonth.of(2027, 8),
                null, null, new BigDecimal("100"), null, "구매");
    }

    private BaselineSimulationResponse.MonthlyResultResponse month(YearMonth yearMonth,
                                                                    boolean cashShortfall,
                                                                    boolean negativeAmortization,
                                                                    String liquidAssets,
                                                                    String investmentAssets,
                                                                    String remainingDebt,
                                                                    String netWorth) {
        BaselineSimulationResponse.MonthlyResultResponse month = mock(
                BaselineSimulationResponse.MonthlyResultResponse.class);
        when(month.yearMonth()).thenReturn(yearMonth);
        when(month.liquidAssets()).thenReturn(new BigDecimal(liquidAssets));
        when(month.investmentAssets()).thenReturn(new BigDecimal(investmentAssets));
        when(month.remainingDebt()).thenReturn(new BigDecimal(remainingDebt));
        when(month.netWorth()).thenReturn(new BigDecimal(netWorth));
        when(month.cashShortfall()).thenReturn(cashShortfall);
        when(month.negativeAmortization()).thenReturn(negativeAmortization);
        return month;
    }
}
