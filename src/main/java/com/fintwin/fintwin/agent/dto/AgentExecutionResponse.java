package com.fintwin.fintwin.agent.dto;

import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentExplanation;
import com.fintwin.fintwin.agent.domain.AgentFailureCode;
import com.fintwin.fintwin.agent.domain.AgentRejectionCode;
import com.fintwin.fintwin.agent.domain.AgentResultType;
import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.AgentTraceStep;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.RiskFinding;
import com.fintwin.fintwin.agent.routing.AgentToolName;

import java.util.List;

public record AgentExecutionResponse(
        AgentStatus status,
        String intent,
        AgentToolName selectedTool,
        AgentRejectionCode rejectionCode,
        AgentFailureCode failureCode,
        List<MissingInformation> missingInformation,
        List<String> clarificationQuestions,
        AgentResultType resultType,
        AgentToolResult typedResult,
        List<RiskFinding> risks,
        AgentExplanation explanation,
        List<AgentTraceStep> trace,
        int toolCallCount,
        String privacyNotice,
        String disclaimer
) {
    public static AgentExecutionResponse from(AgentExecutionResult result) {
        return new AgentExecutionResponse(result.status(), result.intent(), result.selectedTool(),
                result.rejectionCode(), result.failureCode(), result.missingInformation(),
                result.clarificationQuestions(), result.resultType(), result.typedResult(), result.risks(),
                result.explanation(), result.trace(), result.toolCallCount(), result.privacyNotice(),
                result.disclaimer());
    }
}
