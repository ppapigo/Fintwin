package com.fintwin.fintwin.simulation.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.simulation.service.BaselineSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BaselineSimulationControllerTest {
    private BaselineSimulationService simulationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        simulationService = mock(BaselineSimulationService.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(1L);
        BaselineSimulationController controller = new BaselineSimulationController(
                simulationService, currentUserIdProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsValidBaselineRequestUsingCurrentUser() throws Exception {
        mockMvc.perform(post("/api/simulations/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());

        verify(simulationService).simulate(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    void rejectsUnsupportedHorizon() throws Exception {
        mockMvc.perform(post("/api/simulations/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"horizonMonths\": 12", "\"horizonMonths\": 24")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsMissingRequiredAssumption() throws Exception {
        mockMvc.perform(post("/api/simulations/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"annualInflationRate\": 2.0,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsOutOfRangeAssumption() throws Exception {
        mockMvc.perform(post("/api/simulations/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"annualInvestmentReturnRate\": 5.0",
                                "\"annualInvestmentReturnRate\": -100.000001")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsBadRequestWhenDebtPaymentIsMissingForDebtProfile() throws Exception {
        when(simulationService.simulate(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new InvalidRequestException("monthlyDebtPayment is required when debt exists"));

        mockMvc.perform(post("/api/simulations/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"monthlyDebtPayment\": 500.00", "\"monthlyDebtPayment\": null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void returnsNotFoundWhenProfileDoesNotExist() throws Exception {
        when(simulationService.simulate(org.mockito.ArgumentMatchers.eq(1L), any()))
                .thenThrow(new ResourceNotFoundException("Financial profile not found"));

        mockMvc.perform(post("/api/simulations/baseline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private String validJson() {
        return """
                {
                  "startYearMonth": "2026-08",
                  "horizonMonths": 12,
                  "assumptions": {
                    "annualIncomeGrowthRate": 3.0,
                    "annualInflationRate": 2.0,
                    "annualDepositInterestRate": 2.5,
                    "annualInvestmentReturnRate": 5.0,
                    "monthlyDebtPayment": 500.00
                  }
                }
                """;
    }
}
