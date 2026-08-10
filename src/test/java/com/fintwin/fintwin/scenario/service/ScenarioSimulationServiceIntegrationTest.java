package com.fintwin.fintwin.scenario.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.scenario.dto.FinancialEventRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonRequest;
import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ScenarioSimulationServiceIntegrationTest {
    @Autowired
    private ScenarioSimulationService scenarioSimulationService;
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
    void usesLatestProfileSnapshotWithoutMutatingItAndReturnsWhatIfMinusBaselineDelta() {
        userRepository.save(new User(1L));
        financialProfileService.create(1L, createRequest("2000000"));
        FinancialProfileResponse latest = financialProfileService.updateCurrent(1L, updateRequest("3000000"));
        FinancialProfileResponse before = financialProfileService.getCurrent(1L);

        ScenarioComparisonResponse response = scenarioSimulationService.compare(1L, request());
        FinancialProfileResponse after = financialProfileService.getCurrent(1L);

        assertThat(response.financialProfileId()).isEqualTo(latest.id());
        assertThat(response.financialProfileVersion()).isEqualTo(2);
        assertThat(response.baseline().monthlyResults().getFirst().income()).isEqualByComparingTo("3000000.00");
        assertThat(response.finalComparison().netWorthDelta()).isEqualByComparingTo("-1000000.00");
        assertThat(response.impactSummary().netWorthDelta()).isEqualByComparingTo("-1000000.00");
        assertThat(response.normalizedEvents()).extracting(ScenarioComparisonResponse.NormalizedEventResponse::eventId)
                .containsExactly("purchase");
        assertThat(after).isEqualTo(before);
        assertThat(financialProfileRepository.count()).isEqualTo(2);
    }

    private ScenarioComparisonRequest request() {
        YearMonth start = YearMonth.of(2026, 8);
        FinancialEventRequest event = new FinancialEventRequest("purchase", "ONE_TIME_EXPENSE",
                start.plusMonths(2), null, null, decimal("1000000"), null, "purchase");
        return new ScenarioComparisonRequest("purchase scenario", start, 12,
                new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null), List.of(event));
    }

    private FinancialProfileCreateRequest createRequest(String monthlyIncome) {
        return new FinancialProfileCreateRequest(decimal(monthlyIncome), decimal("10000000"), decimal("0"),
                decimal("0"), decimal("0"), decimal("0"), decimal("500000"), decimal("500000"),
                decimal("0"), decimal("0"));
    }

    private FinancialProfileUpdateRequest updateRequest(String monthlyIncome) {
        return new FinancialProfileUpdateRequest(decimal(monthlyIncome), decimal("10000000"), decimal("0"),
                decimal("0"), decimal("0"), decimal("0"), decimal("500000"), decimal("500000"),
                decimal("0"), decimal("0"));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
