package com.fintwin.fintwin.agent.explanation;

import com.fintwin.fintwin.agent.domain.AgentEvidence;
import com.fintwin.fintwin.agent.domain.AgentExplanation;
import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.domain.GoalAgentToolResult;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class RuleBasedExplanationComposer {
    private static final String ASSUMPTION_NOTICE =
            "모든 값은 요청에 포함된 가정과 현재 Financial Profile을 동일하게 적용한 결과입니다.";
    private static final String DISCLAIMER =
            "이 결과는 결정론적 시뮬레이션이며 금융 조언, 수익 예측 또는 목표 달성 보장이 아닙니다.";

    public AgentExplanation compose(AgentToolResult result) {
        return switch (result) {
            case BaselineAgentToolResult baseline -> baseline(baseline);
            case ScenarioAgentToolResult scenario -> scenario(scenario);
            case GoalAgentToolResult goal -> goal(goal);
        };
    }

    private AgentExplanation baseline(BaselineAgentToolResult result) {
        return new AgentExplanation(
                "설정한 가정에 따른 " + result.horizonMonths() + "개월 후 순자산 결과입니다.",
                "최종 순자산은 " + result.finalNetWorth().toPlainString() + "원입니다.",
                List.of(new AgentEvidence("typedResult.finalNetWorth", result.finalNetWorth().toPlainString()),
                        new AgentEvidence("typedResult.finalYearMonth", result.finalYearMonth().toString())),
                ASSUMPTION_NOTICE, DISCLAIMER);
    }

    private AgentExplanation scenario(ScenarioAgentToolResult result) {
        String direction = result.netWorthDelta().signum() < 0 ? "감소했습니다"
                : result.netWorthDelta().signum() > 0 ? "증가했습니다" : "같습니다";
        return new AgentExplanation(
                "What-if 시나리오의 최종 순자산은 기준안보다 " + direction + ".",
                "최종 순자산 차이는 " + result.netWorthDelta().toPlainString() + "원입니다.",
                List.of(new AgentEvidence("typedResult.netWorthDelta", result.netWorthDelta().toPlainString()),
                        new AgentEvidence("typedResult.cumulativeConsumptionDelta",
                                result.cumulativeConsumptionDelta().toPlainString()),
                        new AgentEvidence("typedResult.cumulativeIncomeDelta",
                                result.cumulativeIncomeDelta().toPlainString())),
                ASSUMPTION_NOTICE, DISCLAIMER);
    }

    private AgentExplanation goal(GoalAgentToolResult result) {
        String headline = "ALREADY_ACHIEVABLE".equals(result.goalStatus())
                ? "기준안에서 설정한 목표 순자산에 도달합니다."
                : "설정한 목표 순자산에 대한 역산 결과입니다.";
        return new AgentExplanation(headline,
                "목표 상태는 " + result.goalStatus() + "이며 기준안의 목표 부족액은 "
                        + result.goalGap().toPlainString() + "원입니다.",
                List.of(new AgentEvidence("typedResult.goalStatus", result.goalStatus()),
                        new AgentEvidence("typedResult.goalGap", result.goalGap().toPlainString()),
                        new AgentEvidence("typedResult.baselineFinalNetWorth",
                                result.baselineFinalNetWorth().toPlainString())),
                ASSUMPTION_NOTICE, DISCLAIMER);
    }
}
