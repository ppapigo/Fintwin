package com.fintwin.fintwin.scenario.controller;

import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import com.fintwin.fintwin.scenario.service.ScenarioSimulationService;
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

class ScenarioSimulationControllerTest {
    private ScenarioSimulationService scenarioSimulationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        scenarioSimulationService = mock(ScenarioSimulationService.class);
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScenarioSimulationController(scenarioSimulationService, currentUserIdProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsValidRequestUsingCurrentUser() throws Exception {
        mockMvc.perform(post("/api/simulations/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson()))
                .andExpect(status().isOk());

        verify(scenarioSimulationService).compare(eq(7L), any());
    }

    @Test
    void rejectsMissingEventIdAtControllerBoundary() throws Exception {
        mockMvc.perform(post("/api/simulations/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("\"eventId\": \"purchase\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsEmptyEventListAtControllerBoundary() throws Exception {
        mockMvc.perform(post("/api/simulations/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace(eventJson(), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void mapsConditionalEventValidationFailureToCommonBadRequest() throws Exception {
        when(scenarioSimulationService.compare(eq(7L), any()))
                .thenThrow(new InvalidRequestException("unsupported eventType: NEW_LOAN"));

        mockMvc.perform(post("/api/simulations/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson().replace("ONE_TIME_EXPENSE", "NEW_LOAN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void acceptsMultiScenarioRequestUsingOnlyTheCurrentPrincipal() throws Exception {
        mockMvc.perform(post("/api/simulations/compare-multiple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMultiJson()))
                .andExpect(status().isOk());

        verify(scenarioSimulationService).compareMultiple(eq(7L), any());
    }

    @Test
    void rejectsDuplicateScenarioKeysAndMoreThanFourScenarios() throws Exception {
        String duplicate = validMultiJson().replace("\"scenarioKey\": \"C\"", "\"scenarioKey\": \"B\"");
        mockMvc.perform(post("/api/simulations/compare-multiple")
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String five = multiJson("B", "C", "D", "E", "F");
        mockMvc.perform(post("/api/simulations/compare-multiple")
                        .contentType(MediaType.APPLICATION_JSON).content(five))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsControlCharactersAndInternalIdentifierOrToolFields() throws Exception {
        mockMvc.perform(post("/api/simulations/compare-multiple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validMultiJson().replace("first choice", "first\\nchoice")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        for (String forbidden : new String[]{"userId", "profileId", "toolName"}) {
            String json = validMultiJson().replaceFirst("\\{", "{\\n\\\"" + forbidden + "\\\": 99,");
            mockMvc.perform(post("/api/simulations/compare-multiple")
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    private String validJson() {
        return """
                {
                  "scenarioName": "purchase scenario",
                  "startYearMonth": "2026-08",
                  "horizonMonths": 12,
                  "assumptions": {
                    "annualIncomeGrowthRate": 0,
                    "annualInflationRate": 0,
                    "annualDepositInterestRate": 0,
                    "annualInvestmentReturnRate": 0,
                    "monthlyDebtPayment": 0
                  },
                  "events": [%s]
                }
                """.formatted(eventJson());
    }

    private String eventJson() {
        return """
                {
                  "eventId": "purchase",
                  "eventType": "ONE_TIME_EXPENSE",
                  "effectiveYearMonth": "2026-09",
                  "amount": 1000000,
                  "description": "purchase"
                }
                """;
    }

    private String validMultiJson() {
        return multiJson("B", "C");
    }

    private String multiJson(String... scenarioKeys) {
        String scenarios = java.util.Arrays.stream(scenarioKeys)
                .map(this::scenarioJson).collect(java.util.stream.Collectors.joining(","));
        return """
                {
                  "startYearMonth": "2026-08",
                  "horizonMonths": 12,
                  "assumptions": {
                    "annualIncomeGrowthRate": "0",
                    "annualInflationRate": "0",
                    "annualDepositInterestRate": "0",
                    "annualInvestmentReturnRate": "0",
                    "monthlyDebtPayment": "0"
                  },
                  "scenarios": [%s]
                }
                """.formatted(scenarios);
    }

    private String scenarioJson(String key) {
        return """
                {
                  "scenarioKey": "%s",
                  "label": "%s choice",
                  "events": []
                }
                """.formatted(key, key.equals("B") ? "first" : key);
    }
}
