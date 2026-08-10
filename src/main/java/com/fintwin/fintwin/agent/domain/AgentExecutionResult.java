package com.fintwin.fintwin.agent.domain;

import com.fintwin.fintwin.agent.routing.AgentToolName;

import java.util.List;
import java.util.Objects;

public record AgentExecutionResult(
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
    public AgentExecutionResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(intent);
        missingInformation = List.copyOf(missingInformation);
        clarificationQuestions = List.copyOf(clarificationQuestions);
        risks = List.copyOf(risks);
        trace = List.copyOf(trace);
        Objects.requireNonNull(privacyNotice);
        Objects.requireNonNull(disclaimer);
        if (toolCallCount < 0 || toolCallCount > 1) {
            throw new IllegalArgumentException("Primary tool call count must be between zero and one");
        }
        if ((resultType == null) != (typedResult == null)) {
            throw new IllegalArgumentException("Result type and typed result must be present together");
        }
        if (typedResult != null && resultType != typedResult.resultType()) {
            throw new IllegalArgumentException("Result type must match the typed result");
        }
        validateTerminalContract(status, selectedTool, rejectionCode, failureCode, missingInformation,
                typedResult, explanation, toolCallCount);
    }

    private static void validateTerminalContract(AgentStatus status, AgentToolName selectedTool,
                                                 AgentRejectionCode rejectionCode,
                                                 AgentFailureCode failureCode,
                                                 List<MissingInformation> missingInformation,
                                                 AgentToolResult typedResult,
                                                 AgentExplanation explanation,
                                                 int toolCallCount) {
        switch (status) {
            case COMPLETED -> {
                if (selectedTool == null || typedResult == null || explanation == null || toolCallCount != 1
                        || rejectionCode != null || failureCode != null || !missingInformation.isEmpty()) {
                    throw new IllegalArgumentException("Completed result contract is invalid");
                }
            }
            case NEEDS_INPUT -> {
                if (toolCallCount != 0 || typedResult != null || explanation != null
                        || missingInformation.isEmpty() || rejectionCode != null || failureCode != null) {
                    throw new IllegalArgumentException("Needs-input result contract is invalid");
                }
            }
            case REJECTED -> {
                if (selectedTool != null || rejectionCode == null || failureCode != null || toolCallCount != 0
                        || typedResult != null || explanation != null) {
                    throw new IllegalArgumentException("Rejected result contract is invalid");
                }
            }
            case FAILED -> {
                if (selectedTool == null || rejectionCode != null || failureCode == null
                        || typedResult != null || explanation != null || !missingInformation.isEmpty()) {
                    throw new IllegalArgumentException("Failed result contract is invalid");
                }
            }
        }
    }
}
