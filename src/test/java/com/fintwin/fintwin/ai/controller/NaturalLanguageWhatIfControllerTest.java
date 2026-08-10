package com.fintwin.fintwin.ai.controller;

import com.fintwin.fintwin.ai.dto.NaturalLanguageWhatIfRequest;
import com.fintwin.fintwin.ai.openai.error.AiAdapterException;
import com.fintwin.fintwin.ai.openai.error.AiErrorCode;
import com.fintwin.fintwin.ai.service.NaturalLanguageWhatIfService;
import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NaturalLanguageWhatIfControllerTest {
    private final NaturalLanguageWhatIfService service = mock(NaturalLanguageWhatIfService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NaturalLanguageWhatIfController(service, currentUserIdProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsOnlyStructuredExecutionFieldsAndUsesCurrentUserBoundary() throws Exception {
        mockMvc.perform(post("/api/agent/natural-language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());

        ArgumentCaptor<NaturalLanguageWhatIfRequest> request =
                ArgumentCaptor.forClass(NaturalLanguageWhatIfRequest.class);
        verify(service).execute(eq(7L), request.capture());
        assertThat(request.getValue().assumptions().monthlyDebtPayment())
                .isEqualByComparingTo("300000");
        assertThat(NaturalLanguageWhatIfRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("scenarioText", "startYearMonth", "horizonMonths", "assumptions")
                .doesNotContain("userId", "profileId");
    }

    @Test
    void rejectsMissingAssumptionAndUnsupportedHorizonAtControllerBoundary() throws Exception {
        mockMvc.perform(post("/api/agent/natural-language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"annualInflationRate\": 2.0,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/agent/natural-language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"horizonMonths\": 36", "\"horizonMonths\": 24")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void providerErrorUsesSafeCommonErrorResponse() throws Exception {
        when(service.execute(eq(7L), any())).thenThrow(new AiAdapterException(AiErrorCode.AI_TIMEOUT,
                new IllegalStateException("provider raw body and secret")));

        mockMvc.perform(post("/api/agent/natural-language")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("External AI request timed out"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider raw"))));
    }

    private String validJson() {
        return """
                {
                  "scenarioText": "내년에 3천만 원짜리 자동차를 사면?",
                  "startYearMonth": "2026-08",
                  "horizonMonths": 36,
                  "assumptions": {
                    "annualIncomeGrowthRate": 2.0,
                    "annualInflationRate": 2.0,
                    "annualDepositInterestRate": 2.5,
                    "annualInvestmentReturnRate": 4.0,
                    "monthlyDebtRepayment": 300000
                  }
                }
                """;
    }
}
