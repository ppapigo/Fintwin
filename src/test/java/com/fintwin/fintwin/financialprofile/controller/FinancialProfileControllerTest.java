package com.fintwin.fintwin.financialprofile.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.financialprofile.service.FinancialProfileService;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
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

class FinancialProfileControllerTest {
    private FinancialProfileService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(FinancialProfileService.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(1L);
        FinancialProfileController controller = new FinancialProfileController(service, currentUserIdProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsValidRequest() throws Exception {
        mockMvc.perform(post("/api/financial-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isCreated());

        verify(service).create(org.mockito.ArgumentMatchers.eq(1L), any());
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        mockMvc.perform(post("/api/financial-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("5000000.00", "-1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("monthlyIncome"));
    }

    @Test
    void rejectsMissingRequiredValues() throws Exception {
        mockMvc.perform(post("/api/financial-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String validJson() {
        return """
                {
                  "monthlyIncome": 5000000.00,
                  "cashAssets": 3000000.00,
                  "deposits": 10000000.00,
                  "investmentAssets": 8000000.00,
                  "totalLoanBalance": 10000000.00,
                  "loanInterestRate": 4.2500,
                  "monthlyFixedExpenses": 1500000.00,
                  "monthlyVariableExpenses": 800000.00,
                  "monthlySavings": 700000.00,
                  "monthlyInvestments": 500000.00
                }
                """;
    }
}
