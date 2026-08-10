package com.fintwin.fintwin.ai.dto;

import com.fintwin.fintwin.agent.domain.AgentExecutionResult;
import com.fintwin.fintwin.agent.domain.AgentExplanation;
import com.fintwin.fintwin.agent.domain.AgentResultType;
import com.fintwin.fintwin.agent.domain.AgentStatus;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.AgentTraceStep;
import com.fintwin.fintwin.agent.domain.MissingInformation;
import com.fintwin.fintwin.agent.domain.RiskFinding;
import com.fintwin.fintwin.agent.routing.AgentToolName;
import com.fintwin.fintwin.privacy.domain.PrivacyMode;

import java.util.List;

public record NaturalLanguageWhatIfResponse(
        boolean aiUsed,
        String provider,
        String model,
        PrivacyMode privacyMode,
        boolean financialValuesTokenized,
        AgentStatus agentStatus,
        AgentToolName selectedTool,
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
    public NaturalLanguageWhatIfResponse {
        missingInformation = List.copyOf(missingInformation);
        clarificationQuestions = List.copyOf(clarificationQuestions);
        risks = List.copyOf(risks);
        trace = List.copyOf(trace);
    }

    public static NaturalLanguageWhatIfResponse completed(String provider, String model,
                                                          boolean financialValuesTokenized,
                                                          AgentExecutionResult result) {
        return new NaturalLanguageWhatIfResponse(true, provider, model, PrivacyMode.STRICT,
                financialValuesTokenized, result.status(), result.selectedTool(), result.missingInformation(),
                result.clarificationQuestions(), result.resultType(), result.typedResult(), result.risks(),
                result.explanation(), result.trace(), result.toolCallCount(), result.privacyNotice(),
                result.disclaimer());
    }

    public static NaturalLanguageWhatIfResponse needsInput(String provider, String model,
                                                           boolean financialValuesTokenized,
                                                           List<MissingInformation> missingInformation,
                                                           List<AgentTraceStep> trace) {
        return new NaturalLanguageWhatIfResponse(true, provider, model, PrivacyMode.STRICT,
                financialValuesTokenized, AgentStatus.NEEDS_INPUT, AgentToolName.SCENARIO_COMPARISON_TOOL,
                missingInformation, missingInformation.stream().map(MissingInformation::question).toList(),
                null, null, List.of(), null, trace, 0,
                "금융 원문, 실제 Reference 값, Profile과 계산 결과는 외부 AI Payload에 포함되지 않습니다.",
                "외부 AI는 이벤트 구조화만 수행하며 금융 계산과 설명은 로컬 결정론적 엔진이 담당합니다.");
    }
}
