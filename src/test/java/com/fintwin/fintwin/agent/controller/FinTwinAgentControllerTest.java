package com.fintwin.fintwin.agent.controller;

import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentEvidence;
import com.fintwin.fintwin.agent.domain.AgentExplanation;
import com.fintwin.fintwin.agent.domain.AgentResultType;
import com.fintwin.fintwin.agent.domain.AgentState;
import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.AgentTraceStep;
import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.orchestration.FinTwinAgentOrchestrator;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.auth.CurrentUserIdProvider;
import com.fintwin.fintwin.global.error.GlobalExceptionHandler;
import com.fintwin.fintwin.global.error.InvalidRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinTwinAgentControllerTest {
    private final FinTwinAgentOrchestrator orchestrator = mock(FinTwinAgentOrchestrator.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CurrentUserIdProvider currentUserIdProvider = mock(CurrentUserIdProvider.class);
        when(currentUserIdProvider.currentUserId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FinTwinAgentController(orchestrator, currentUserIdProvider))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsOnlyTheSelectedStronglyTypedResultAndUsesCurrentUserBoundary() throws Exception {
        BaselineAgentToolResult typedResult = new BaselineAgentToolResult(YearMonth.of(2026, 8), 12,
                YearMonth.of(2027, 7), new BigDecimal("100"), new BigDecimal("200"), BigDecimal.ZERO,
                new BigDecimal("300"), List.of(), List.of());
        AgentExecutionResult result = new AgentExecutionResult(AgentStatus.COMPLETED,
                "BASELINE_SIMULATION", AgentToolName.BASELINE_SIMULATION_TOOL, null, null, List.of(), List.of(),
                AgentResultType.BASELINE, typedResult, List.of(), new AgentExplanation("결과", "요약",
                        List.of(new AgentEvidence("typedResult.finalNetWorth", "300")), "가정", "면책"),
                List.of(new AgentTraceStep(1, AgentState.RECEIVED, "orchestrator", "ACCEPTED")),
                1, "내부 처리", "결정론적 결과");
        when(orchestrator.execute(eq(7L), any())).thenReturn(result);

        mockMvc.perform(post("/api/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("BASELINE"))
                .andExpect(jsonPath("$.typedResult.finalNetWorth").value(300))
                .andExpect(jsonPath("$.typedResult.baselineFinalNetWorth").doesNotExist())
                .andExpect(jsonPath("$.typedResult.goalStatus").doesNotExist())
                .andExpect(jsonPath("$.toolCallCount").value(1));

        verify(orchestrator).execute(eq(7L), any());
    }

    @Test
    void structurallyInvalidEnumFromExistingValidatorUsesCommonBadRequest() throws Exception {
        when(orchestrator.execute(eq(7L), any()))
                .thenThrow(new InvalidRequestException("unsupported eventType: UNKNOWN"));

        mockMvc.perform(post("/api/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String validRequest() {
        return """
                {
                  "intent": "BASELINE_SIMULATION",
                  "startYearMonth": "2026-08",
                  "horizonMonths": 12,
                  "assumptions": {
                    "annualIncomeGrowthRate": 0,
                    "annualInflationRate": 0,
                    "annualDepositInterestRate": 0,
                    "annualInvestmentReturnRate": 0,
                    "monthlyDebtPayment": 0
                  }
                }
                """;
    }
}
