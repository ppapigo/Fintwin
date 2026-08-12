package com.fintwin.fintwin.marketstress.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.marketstress.marketdata.MarketContext;
import com.fintwin.fintwin.marketstress.marketdata.MarketContextService;
import com.fintwin.fintwin.marketstress.marketdata.MarketContextStatus;
import com.fintwin.fintwin.marketstress.service.MarketStressSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketStressControllerTest {
    private MarketStressSimulationService simulationService;
    private MarketContextService contextService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        simulationService = mock(MarketStressSimulationService.class);
        contextService = mock(MarketContextService.class);
        CurrentUserIdProvider currentUser = mock(CurrentUserIdProvider.class);
        when(currentUser.currentUserId()).thenReturn(7L);
        when(contextService.currentContext()).thenReturn(new MarketContext(MarketContextStatus.UNAVAILABLE,
                Instant.parse("2026-08-11T00:00:00Z"), List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MarketStressController(contextService, simulationService, currentUser))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsOfficialMarketContextStatusWithoutRequiringCredentials() throws Exception {
        mockMvc.perform(get("/api/market-stress/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.usageBoundary").isNotEmpty());
    }

    @Test
    void acceptsValidStressRequestForCurrentPrincipal() throws Exception {
        mockMvc.perform(post("/api/market-stress/simulate")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isOk());

        verify(simulationService).simulate(eq(7L), any());
    }

    @Test
    void rejectsShockOutsideSimulationPeriod() throws Exception {
        mockMvc.perform(post("/api/market-stress/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"2026-10\"", "\"2027-08\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsPositiveEquityShockAndNegativeExposure() throws Exception {
        mockMvc.perform(post("/api/market-stress/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"domesticStockAmount\": 3000000",
                                        "\"domesticStockAmount\": -1")
                                .replace("\"domesticStockShockRate\": -20",
                                        "\"domesticStockShockRate\": 1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String validJson() {
        return """
                {
                  "startYearMonth": "2026-08",
                  "horizonMonths": 12,
                  "assumptions": {
                    "annualIncomeGrowthRate": 0,
                    "annualInflationRate": 0,
                    "annualDepositInterestRate": 0,
                    "annualInvestmentReturnRate": 0,
                    "monthlyDebtPayment": 300000
                  },
                  "exposure": {
                    "domesticStockAmount": 3000000,
                    "overseasStockAmount": 2000000
                  },
                  "stressScenario": {
                    "shockYearMonth": "2026-10",
                    "domesticStockShockRate": -20,
                    "overseasStockShockRate": -25,
                    "krwUsdExchangeRateShockRate": 10,
                    "loanInterestRateChangePercentagePoints": 2
                  },
                  "targetNetWorth": 20000000
                }
                """;
    }
}
