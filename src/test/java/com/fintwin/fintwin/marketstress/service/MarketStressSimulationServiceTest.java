package com.fintwin.fintwin.marketstress.service;

import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.marketstress.dto.MarketStressSimulationRequest;
import com.fintwin.fintwin.marketstress.dto.MarketStressSimulationResponse;
import com.fintwin.fintwin.marketstress.engine.MarketStressSimulationEngine;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import com.fintwin.fintwin.simulation.service.SimulationDomainMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MarketStressSimulationServiceTest {
    private final FinancialProfileService profileService = mock(FinancialProfileService.class);
    private final MarketStressSimulationService service = new MarketStressSimulationService(profileService,
            new SimulationDomainMapper(),
            new MarketStressSimulationEngine(new MonthlyFinancialSimulationEngine()));

    @Test
    void usesCurrentProfileOnceAndDoesNotExposeInternalIdentifiers() {
        when(profileService.getCurrent(7L)).thenReturn(profile());

        MarketStressSimulationResponse response = service.simulate(7L, request("3000000", "2000000"));

        verify(profileService).getCurrent(7L);
        verifyNoMoreInteractions(profileService);
        assertThat(response.financialProfileVersion()).isEqualTo(4);
        assertThat(response.getClass().getRecordComponents()).extracting(component -> component.getName())
                .doesNotContain("financialProfileId", "userId", "profileId");
        assertThat(response.marketContextUsage().usedInCalculation()).isFalse();
        assertThat(response.baseline().monthlyResults()).hasSize(12);
        assertThat(response.stressed().monthlyResults()).hasSize(12);
    }

    @Test
    void rejectsStockExposureAboveProfileInvestmentAssets() {
        when(profileService.getCurrent(7L)).thenReturn(profile());

        assertThatThrownBy(() -> service.simulate(7L, request("8000000", "3000000")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Stock exposure cannot exceed current investment assets");
    }

    private MarketStressSimulationRequest request(String domestic, String overseas) {
        return new MarketStressSimulationRequest(YearMonth.of(2026, 8), 12,
                new BaselineSimulationRequest.Assumptions(decimal("0"), decimal("0"), decimal("0"),
                        decimal("0"), decimal("300000")),
                new MarketStressSimulationRequest.Exposure(decimal(domestic), decimal(overseas)),
                new MarketStressSimulationRequest.StressScenario(YearMonth.of(2026, 10), decimal("-20"),
                        decimal("-25"), decimal("10"), decimal("2")), decimal("20000000"));
    }

    private FinancialProfileResponse profile() {
        return new FinancialProfileResponse(99L, 7L, 4, 98L, decimal("4000000"), decimal("5000000"),
                decimal("2000000"), decimal("10000000"), decimal("12000000"), decimal("6"),
                decimal("1500000"), decimal("800000"), decimal("500000"), decimal("500000"), Instant.EPOCH);
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value); }
}
