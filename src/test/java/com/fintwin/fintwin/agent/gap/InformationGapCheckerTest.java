package com.fintwin.fintwin.agent.gap;

import com.fintwin.fintwin.agent.domain.AgentCommand;
import com.fintwin.fintwin.agent.domain.AgentIntent;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.MissingInformationCode;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InformationGapCheckerTest {
    private final FinancialProfileService profileService = mock(FinancialProfileService.class);
    private final InformationGapChecker checker = new InformationGapChecker(profileService);

    @BeforeEach
    void noProfileByDefault() {
        when(profileService.getCurrentIfPresent(7L)).thenReturn(Optional.empty());
    }

    @Test
    void reportsCommonMissingWithoutCreatingDefaults() {
        AgentCommand command = new AgentCommand(AgentIntent.BASELINE_SIMULATION, null, null,
                null, null, null, null);

        List<MissingInformation> result = checker.check(7L, command);

        assertThat(result).extracting(MissingInformation::code).containsExactly(
                MissingInformationCode.START_YEAR_MONTH_REQUIRED,
                MissingInformationCode.HORIZON_REQUIRED,
                MissingInformationCode.ASSUMPTIONS_REQUIRED);
        assertThat(command.startYearMonth()).isNull();
        assertThat(command.horizonMonths()).isNull();
        assertThat(command.assumptions()).isNull();
    }

    @Test
    void reportsWhatIfEventAndItsAmountAndDateGaps() {
        AgentCommand noEvents = command(AgentIntent.WHAT_IF_SIMULATION, null, null, null);
        assertThat(checker.check(7L, noEvents)).extracting(MissingInformation::code)
                .containsExactly(MissingInformationCode.EVENTS_REQUIRED);

        FinancialEventRequest incomplete = new FinancialEventRequest("event-1", "ONE_TIME_EXPENSE",
                null, null, null, null, null, "자동차 구매");
        AgentCommand incompleteEvent = command(AgentIntent.WHAT_IF_SIMULATION, List.of(incomplete), null, null);
        assertThat(checker.check(7L, incompleteEvent)).extracting(MissingInformation::code)
                .containsExactly(MissingInformationCode.EVENT_AMOUNT_REQUIRED,
                        MissingInformationCode.EVENT_DATE_REQUIRED);
    }

    @Test
    void reportsPeriodAndGoalGaps() {
        FinancialEventRequest incompletePeriod = new FinancialEventRequest("event-1", "INCOME_PAUSE",
                null, null, null, null, null, "소득 중단");
        assertThat(checker.check(7L, command(AgentIntent.WHAT_IF_SIMULATION,
                List.of(incompletePeriod), null, null))).extracting(MissingInformation::code)
                .containsExactly(MissingInformationCode.EVENT_PERIOD_REQUIRED,
                        MissingInformationCode.EVENT_PERIOD_REQUIRED);

        assertThat(checker.check(7L, command(AgentIntent.GOAL_REVERSE_SIMULATION,
                null, null, null))).extracting(MissingInformation::code)
                .containsExactly(MissingInformationCode.GOAL_TYPE_REQUIRED,
                        MissingInformationCode.TARGET_AMOUNT_REQUIRED);
    }

    @Test
    void reportsDebtPaymentFromSafeProfileServiceBoundary() {
        FinancialProfileResponse profile = mock(FinancialProfileResponse.class);
        when(profile.totalLoanBalance()).thenReturn(new BigDecimal("1000000"));
        when(profileService.getCurrentIfPresent(7L)).thenReturn(Optional.of(profile));
        AgentCommand command = new AgentCommand(AgentIntent.BASELINE_SIMULATION, YearMonth.of(2026, 8), 36,
                assumptions(null), null, null, null);

        assertThat(checker.check(7L, command)).extracting(MissingInformation::code)
                .containsExactly(MissingInformationCode.MONTHLY_DEBT_PAYMENT_REQUIRED);
    }

    @Test
    void rejectsFieldsThatConflictWithIntent() {
        FinancialEventRequest event = new FinancialEventRequest("event-1", "ONE_TIME_EXPENSE",
                YearMonth.of(2027, 8), null, null, new BigDecimal("100"), null, "구매");
        AgentCommand baselineWithEvents = command(AgentIntent.BASELINE_SIMULATION, List.of(event), null, null);
        AgentCommand whatIfWithGoal = command(AgentIntent.WHAT_IF_SIMULATION, List.of(event),
                "TARGET_NET_WORTH", BigDecimal.TEN);
        AgentCommand goalWithEvents = command(AgentIntent.GOAL_REVERSE_SIMULATION, List.of(event),
                "TARGET_NET_WORTH", BigDecimal.TEN);

        assertThatThrownBy(() -> checker.check(7L, baselineWithEvents))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> checker.check(7L, whatIfWithGoal))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> checker.check(7L, goalWithEvents))
                .isInstanceOf(InvalidRequestException.class);
    }

    private AgentCommand command(AgentIntent intent, List<FinancialEventRequest> events,
                                 String goalType, BigDecimal targetAmount) {
        return new AgentCommand(intent, YearMonth.of(2026, 8), 36,
                assumptions(new BigDecimal("500000")), events, goalType, targetAmount);
    }

    private BaselineSimulationRequest.Assumptions assumptions(BigDecimal debtPayment) {
        return new BaselineSimulationRequest.Assumptions(new BigDecimal("3"), new BigDecimal("2"),
                new BigDecimal("2.5"), new BigDecimal("5"), debtPayment);
    }
}
