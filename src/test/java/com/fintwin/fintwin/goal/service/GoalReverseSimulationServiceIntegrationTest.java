package com.fintwin.fintwin.goal.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationRequest;
import com.fintwin.fintwin.goal.dto.GoalReverseSimulationResponse;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
import com.fintwin.fintwin.simulation.service.BaselineSimulationService;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GoalReverseSimulationServiceIntegrationTest {
    private static final YearMonth START = YearMonth.of(2026, 8);

    @Autowired
    private GoalReverseSimulationService goalService;
    @Autowired
    private BaselineSimulationService baselineService;
    @Autowired
    private FinancialProfileService financialProfileService;
    @Autowired
    private FinancialProfileRepository financialProfileRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void usesLatestProfileKeepsSnapshotsImmutableAndMatchesBaselineApi() {
        userRepository.save(new User(1L));
        financialProfileService.create(1L, createRequest("2000000"));
        FinancialProfileResponse latest = financialProfileService.updateCurrent(1L, updateRequest("3000000"));
        FinancialProfileResponse before = financialProfileService.getCurrent(1L);
        GoalReverseSimulationRequest request = request("TARGET_NET_WORTH", "50000000", 12);

        GoalReverseSimulationResponse response = goalService.reverseSimulate(1L, request);
        BaselineSimulationResponse directBaseline = baselineService.simulate(1L,
                new BaselineSimulationRequest(START, 12, request.assumptions()));
        FinancialProfileResponse after = financialProfileService.getCurrent(1L);

        assertThat(response.financialProfileId()).isEqualTo(latest.id());
        assertThat(response.financialProfileVersion()).isEqualTo(2);
        assertThat(response.targetEndYearMonth()).isEqualTo(START.plusMonths(11));
        assertThat(response.baseline()).isEqualTo(directBaseline);
        assertThat(response.goalGap()).isEqualByComparingTo(
                response.targetAmount().subtract(response.baselineFinalNetWorth()).max(BigDecimal.ZERO));
        assertThat(response.plans()).allSatisfy(plan -> assertThat(plan.goalMargin())
                .isEqualByComparingTo(plan.projectedFinalNetWorth().subtract(response.targetAmount())));
        assertThat(after).isEqualTo(before);
        assertThat(financialProfileRepository.count()).isEqualTo(2);
    }

    @Test
    void returnsNotFoundWhenCurrentProfileDoesNotExist() {
        userRepository.save(new User(1L));

        assertThatThrownBy(() -> goalService.reverseSimulate(1L,
                request("TARGET_NET_WORTH", "1000000", 12)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial profile not found");
    }

    @Test
    void rejectsUnsupportedGoalTypeAndInvalidDirectRequest() {
        assertThatThrownBy(() -> goalService.reverseSimulate(1L, request("TARGET_CASH", "1000000", 12)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("unsupported goalType: TARGET_CASH");
        assertThatThrownBy(() -> goalService.reverseSimulate(1L, request("TARGET_NET_WORTH", "0", 12)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("targetAmount must be greater than zero");
        assertThatThrownBy(() -> goalService.reverseSimulate(1L, request("TARGET_NET_WORTH", "1000", 24)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("horizonMonths must be one of 12, 36, or 60");
    }

    @Test
    void requiresDebtPaymentWhenLatestProfileHasDebt() {
        userRepository.save(new User(1L));
        financialProfileService.create(1L, createRequestWithDebt("1000000"));

        assertThatThrownBy(() -> goalService.reverseSimulate(1L,
                request("TARGET_NET_WORTH", "1000000", 12)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("monthlyDebtPayment is required when debt exists");
    }

    private GoalReverseSimulationRequest request(String goalType, String target, int horizon) {
        return new GoalReverseSimulationRequest(goalType, decimal(target), START, horizon,
                new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null));
    }

    private FinancialProfileCreateRequest createRequest(String income) {
        return new FinancialProfileCreateRequest(decimal(income), decimal("1000000"), decimal("500000"),
                decimal("200000"), decimal("0"), decimal("0"), decimal("500000"), decimal("500000"),
                decimal("200000"), decimal("100000"));
    }

    private FinancialProfileCreateRequest createRequestWithDebt(String debt) {
        return new FinancialProfileCreateRequest(decimal("2000000"), decimal("1000000"), decimal("500000"),
                decimal("200000"), decimal(debt), decimal("6"), decimal("500000"), decimal("500000"),
                decimal("200000"), decimal("100000"));
    }

    private FinancialProfileUpdateRequest updateRequest(String income) {
        return new FinancialProfileUpdateRequest(decimal(income), decimal("1000000"), decimal("500000"),
                decimal("200000"), decimal("0"), decimal("0"), decimal("500000"), decimal("500000"),
                decimal("200000"), decimal("100000"));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
