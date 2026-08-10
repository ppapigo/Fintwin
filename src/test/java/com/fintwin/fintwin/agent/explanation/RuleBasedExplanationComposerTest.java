package com.fintwin.fintwin.agent.explanation;

import com.fintwin.fintwin.agent.domain.AgentEvidence;
import com.fintwin.fintwin.agent.domain.AgentExplanation;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedExplanationComposerTest {
    @Test
    void explanationUsesExactToolNumbersAndEvidencePathsWithoutAdviceOrGuarantees() {
        ScenarioAgentToolResult result = new ScenarioAgentToolResult(YearMonth.of(2026, 8), 36,
                YearMonth.of(2029, 7), new BigDecimal("500.00"), new BigDecimal("376.55"),
                new BigDecimal("-123.45"), new BigDecimal("-120.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("123.45"), List.of(), List.of(), List.of());

        AgentExplanation explanation = new RuleBasedExplanationComposer().compose(result);

        assertThat(explanation.summary()).contains("-123.45");
        assertThat(explanation.evidence()).extracting(AgentEvidence::fieldPath)
                .contains("typedResult.netWorthDelta", "typedResult.cumulativeConsumptionDelta");
        assertThat(explanation.toString()).doesNotContain("반드시", "확실히", "안전한 투자", "추천",
                "보장합니다");
    }
}
