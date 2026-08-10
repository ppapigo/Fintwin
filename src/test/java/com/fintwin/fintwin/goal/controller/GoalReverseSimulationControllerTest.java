package com.fintwin.fintwin.goal.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.goal.service.GoalReverseSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoalReverseSimulationControllerTest {
    private GoalReverseSimulationService goalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        goalService = mock(GoalReverseSimulationService.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(9L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new GoalReverseSimulationController(goalService, currentUserIdProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsValidRequestUsingCurrentUser() throws Exception {
        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());

        verify(goalService).reverseSimulate(eq(9L), any());
    }

    @Test
    void rejectsZeroAndNegativeTargetAmount() throws Exception {
        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("50000000", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("50000000", "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsUnsupportedHorizonAndMissingAssumption() throws Exception {
        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"horizonMonths\": 36", "\"horizonMonths\": 24")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"annualInflationRate\": 2.0,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void mapsUnsupportedGoalTypeToCommonBadRequest() throws Exception {
        when(goalService.reverseSimulate(eq(9L), any()))
                .thenThrow(new InvalidRequestException("unsupported goalType: TARGET_CASH"));

        mockMvc.perform(post("/api/goals/reverse-simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("TARGET_NET_WORTH", "TARGET_CASH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String validJson() {
        return """
                {
                  "goalType": "TARGET_NET_WORTH",
                  "targetAmount": 50000000,
                  "startYearMonth": "2026-08",
                  "horizonMonths": 36,
                  "assumptions": {
                    "annualIncomeGrowthRate": 3.0,
                    "annualInflationRate": 2.0,
                    "annualDepositInterestRate": 2.5,
                    "annualInvestmentReturnRate": 5.0,
                    "monthlyDebtPayment": 500000
                  }
                }
                """;
    }
}
