package com.fintwin.fintwin.scenario.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.scenario.dto.MultiScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.MultiScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.scenario.engine.ScenarioMonthlyAdjustmentProvider;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import com.fintwin.fintwin.simulation.service.SimulationDomainMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiScenarioSimulationServiceTest {
    private static final YearMonth START = YearMonth.of(2026, 8);
    private FinancialProfileService profileService;
    private MonthlyFinancialSimulationEngine engine;
    private ScenarioSimulationService service;

    @BeforeEach
    void setUp() {
        profileService = mock(FinancialProfileService.class);
        engine = spy(new MonthlyFinancialSimulationEngine());
        when(profileService.getCurrent(7L)).thenReturn(profile(7L, "3000000"));
        service = new ScenarioSimulationService(profileService, new FinancialEventMapper(),
                new SimulationDomainMapper(), engine);
    }

    @Test
    void loadsProfileOnceAndRunsOneBaselinePlusOneEngineCallPerScenario() {
        MultiScenarioComparisonResponse response = service.compareMultiple(7L, multiRequest(60));

        verify(profileService, times(1)).getCurrent(7L);
        verify(engine, times(1)).simulate(any(), any(), eq(START), eq(60));
        verify(engine, times(4)).simulate(any(), any(), eq(START), eq(60),
                argThat(provider -> provider instanceof ScenarioMonthlyAdjustmentProvider));
        assertThat(response.financialProfileVersion()).isEqualTo(3);
        assertThat(response.scenarios()).extracting(MultiScenarioComparisonResponse.ScenarioResult::scenarioKey)
                .containsExactly("B", "C", "D", "E");
        assertThat(response.scenarios().getFirst().baselineDelta().netWorthDelta()).isEqualByComparingTo("0.00");
    }

    @Test
    void goldenScenariosExactlyMatchTheExistingSingleComparisonContract() {
        MultiScenarioComparisonResponse multi = service.compareMultiple(7L, multiRequest(60));
        List<MultiScenarioComparisonRequest.ScenarioRequest> requested = multiRequest(60).scenarios();

        assertThat(multi.baseline().monthlyResults()).hasSize(60);
        for (int index = 1; index < requested.size(); index++) {
            MultiScenarioComparisonRequest.ScenarioRequest scenario = requested.get(index);
            ScenarioComparisonResponse single = service.compare(7L, new ScenarioComparisonRequest(
                    scenario.label(), START, 60, assumptions(), scenario.events()));
            MultiScenarioComparisonResponse.ScenarioResult actual = multi.scenarios().get(index);
            assertThat(actual.monthlyResults()).isEqualTo(single.whatIf().monthlyResults());
            assertThat(actual.checkpoints()).isEqualTo(single.whatIf().checkpoints());
            assertThat(actual.baselineDelta()).isEqualTo(single.finalComparison());
            assertThat(actual.residualDelta()).isEqualByComparingTo(single.impactSummary().residualDelta());
            assertThat(multi.baseline().monthlyResults()).isEqualTo(single.baseline().monthlyResults());
        }
    }

    @Test
    void eventInputOrderDoesNotChangeNormalizedEventsOrCalculationResults() {
        List<FinancialEventRequest> events = new ArrayList<>(List.of(oneTime(), expenseReduction()));
        MultiScenarioComparisonResponse first = service.compareMultiple(7L,
                requestWithScenarios(List.of(scenario("B", "order", events)), 12));
        Collections.reverse(events);
        MultiScenarioComparisonResponse second = service.compareMultiple(7L,
                requestWithScenarios(List.of(scenario("B", "order", events)), 12));

        assertThat(first.scenarios().getFirst().normalizedEvents())
                .isEqualTo(second.scenarios().getFirst().normalizedEvents());
        assertThat(first.scenarios().getFirst().monthlyResults())
                .isEqualTo(second.scenarios().getFirst().monthlyResults());
    }

    @Test
    void responseOmitsInternalIdentifiersAndStaysBelowTwoMebibytesForMaximumRun() throws Exception {
        MultiScenarioComparisonResponse response = service.compareMultiple(7L, multiRequest(60));
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(response);

        assertThat(json).doesNotContain("financialProfileId", "userId", "profileId", "sessionId", "oauth");
        assertThat(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(2 * 1024 * 1024);
    }

    @Test
    void isolatesUsersByPassingOnlyTheAuthenticatedInternalUserToProfileLookup() {
        when(profileService.getCurrent(8L)).thenReturn(profile(8L, "9000000"));

        MultiScenarioComparisonResponse first = service.compareMultiple(7L,
                requestWithScenarios(List.of(scenario("B", "same", List.of(oneTime()))), 12));
        MultiScenarioComparisonResponse second = service.compareMultiple(8L,
                requestWithScenarios(List.of(scenario("B", "same", List.of(oneTime()))), 12));

        verify(profileService).getCurrent(7L);
        verify(profileService).getCurrent(8L);
        assertThat(first.baseline().monthlyResults().getFirst().income()).isEqualByComparingTo("3000000.00");
        assertThat(second.baseline().monthlyResults().getFirst().income()).isEqualByComparingTo("9000000.00");
    }

    @Test
    void rejectsDuplicateEventIdsAndOutOfRangeOrUnsupportedEventsBeforeScenarioExecution() {
        FinancialEventRequest duplicate = new FinancialEventRequest("one-time", "EXTRA_DEBT_REPAYMENT", START,
                null, null, decimal("1"), null, "duplicate");
        assertThatThrownBy(() -> service.compareMultiple(7L, requestWithScenarios(List.of(
                scenario("B", "duplicate", List.of(oneTime(), duplicate))), 12)))
                .hasMessageContaining("duplicate eventId");

        FinancialEventRequest outside = new FinancialEventRequest("outside", "ONE_TIME_EXPENSE",
                START.minusMonths(1), null, null, decimal("1"), null, "outside");
        assertThatThrownBy(() -> service.compareMultiple(7L, requestWithScenarios(List.of(
                scenario("B", "outside", List.of(outside))), 12)))
                .hasMessageContaining("outside the simulation range");

        FinancialEventRequest unsupported = new FinancialEventRequest("unsupported", "NEW_LOAN", START,
                null, null, decimal("1"), null, "unsupported");
        assertThatThrownBy(() -> service.compareMultiple(7L, requestWithScenarios(List.of(
                scenario("B", "unsupported", List.of(unsupported))), 12)))
                .hasMessageContaining("unsupported eventType");
    }

    private MultiScenarioComparisonRequest multiRequest(int horizon) {
        return requestWithScenarios(List.of(
                scenario("B", "현재와 같은 대안", List.of()),
                scenario("C", "일회성 지출", List.of(oneTime())),
                scenario("D", "월 소비 절감", List.of(expenseReduction())),
                scenario("E", "소득 중단", List.of(incomePause()))), horizon);
    }

    private MultiScenarioComparisonRequest requestWithScenarios(
            List<MultiScenarioComparisonRequest.ScenarioRequest> scenarios, int horizon) {
        return new MultiScenarioComparisonRequest(START, horizon, assumptions(), scenarios);
    }

    private MultiScenarioComparisonRequest.ScenarioRequest scenario(String key, String label,
                                                                    List<FinancialEventRequest> events) {
        return new MultiScenarioComparisonRequest.ScenarioRequest(key, label, events);
    }

    private FinancialEventRequest oneTime() {
        return new FinancialEventRequest("one-time", "ONE_TIME_EXPENSE", START.plusMonths(1), null, null,
                decimal("1000000"), null, "합성 일회성 지출");
    }

    private FinancialEventRequest expenseReduction() {
        return new FinancialEventRequest("expense", "RECURRING_EXPENSE_CHANGE", null, START,
                START.plusMonths(5), null, decimal("-100000"), "합성 월 소비 절감");
    }

    private FinancialEventRequest incomePause() {
        return new FinancialEventRequest("pause", "INCOME_PAUSE", null, START.plusMonths(2),
                START.plusMonths(4), null, null, "합성 소득 중단");
    }

    private BaselineSimulationRequest.Assumptions assumptions() {
        return new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, decimal("300000"));
    }

    private FinancialProfileResponse profile(Long userId, String income) {
        return new FinancialProfileResponse(99L + userId, userId, 3, null, decimal(income), decimal("5000000"),
                decimal("10000000"), decimal("8000000"), decimal("2000000"), decimal("4"),
                decimal("900000"), decimal("500000"), decimal("400000"), decimal("200000"), Instant.EPOCH);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
