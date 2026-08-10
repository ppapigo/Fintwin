package com.fintwin.fintwin.simulation.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;
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
class BaselineSimulationServiceIntegrationTest {
    @Autowired
    private BaselineSimulationService simulationService;
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
    void usesLatestProfileVersionWithoutChangingSnapshot() {
        userRepository.save(new User(1L));
        financialProfileService.create(1L, createRequest("500.00", "0.00"));
        FinancialProfileResponse versionTwo = financialProfileService.updateCurrent(1L,
                updateRequest("600.00", "0.00"));
        FinancialProfileResponse beforeSimulation = financialProfileService.getCurrent(1L);

        BaselineSimulationResponse response = simulationService.simulate(1L, baselineRequest(BigDecimal.ZERO));
        FinancialProfileResponse afterSimulation = financialProfileService.getCurrent(1L);

        assertThat(response.financialProfileId()).isEqualTo(versionTwo.id());
        assertThat(response.financialProfileVersion()).isEqualTo(2);
        assertThat(response.monthlyResults().getFirst().income()).isEqualByComparingTo("600.00");
        assertThat(afterSimulation).isEqualTo(beforeSimulation);
    }

    @Test
    void rejectsMissingDebtPaymentWhenProfileHasDebt() {
        userRepository.save(new User(1L));
        financialProfileService.create(1L, createRequest("500.00", "1000.00"));

        assertThatThrownBy(() -> simulationService.simulate(1L, baselineRequest(null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("monthlyDebtPayment is required when debt exists");
    }

    @Test
    void treatsMissingDebtPaymentAsZeroWhenProfileHasNoDebt() {
        userRepository.save(new User(1L));
        financialProfileService.create(1L, createRequest("500.00", "0.00"));

        BaselineSimulationResponse response = simulationService.simulate(1L, baselineRequest(null));

        assertThat(response.assumptions().monthlyDebtPayment()).isEqualByComparingTo("0");
        assertThat(response.monthlyResults().getFirst().debtPayment()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsSimulationWhenCurrentProfileDoesNotExist() {
        userRepository.save(new User(1L));

        assertThatThrownBy(() -> simulationService.simulate(1L, baselineRequest(BigDecimal.ZERO)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Financial profile not found");
    }

    private BaselineSimulationRequest baselineRequest(BigDecimal monthlyDebtPayment) {
        return new BaselineSimulationRequest(YearMonth.of(2026, 8), 12,
                new BaselineSimulationRequest.Assumptions(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, monthlyDebtPayment));
    }

    private FinancialProfileCreateRequest createRequest(String monthlyIncome, String debt) {
        return new FinancialProfileCreateRequest(
                decimal(monthlyIncome), decimal("1000.00"), decimal("500.00"), decimal("200.00"), decimal(debt),
                decimal("6.0000"), decimal("100.00"), decimal("50.00"), decimal("20.00"), decimal("30.00"));
    }

    private FinancialProfileUpdateRequest updateRequest(String monthlyIncome, String debt) {
        return new FinancialProfileUpdateRequest(
                decimal(monthlyIncome), decimal("1000.00"), decimal("500.00"), decimal("200.00"), decimal(debt),
                decimal("6.0000"), decimal("100.00"), decimal("50.00"), decimal("20.00"), decimal("30.00"));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
