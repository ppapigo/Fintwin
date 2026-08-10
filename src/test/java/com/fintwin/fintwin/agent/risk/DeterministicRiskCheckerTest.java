package com.fintwin.fintwin.agent.risk;

import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.domain.GoalAgentToolResult;
import com.fintwin.fintwin.agent.domain.RiskCode;
import com.fintwin.fintwin.agent.domain.RiskFinding;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import com.fintwin.fintwin.agent.domain.ScenarioComparisonDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeterministicRiskCheckerTest {
    private final DeterministicRiskChecker checker = new DeterministicRiskChecker();

    @Test
    void detectsOnlyFlagsAndDeltasAlreadyPresentInSimulationResult() {
        ScenarioAgentToolResult result = new ScenarioAgentToolResult(YearMonth.of(2026, 8), 36,
                YearMonth.of(2029, 7), money("100"), money("80"), money("-20"), money("-10"),
                BigDecimal.ZERO, BigDecimal.ZERO, money("20"), List.of(YearMonth.of(2027, 1)),
                List.of(YearMonth.of(2027, 2)), List.of(), mock(ScenarioComparisonDetails.class));

        assertThat(checker.check(result)).extracting(RiskFinding::code).containsExactly(
                RiskCode.CASH_SHORTFALL, RiskCode.NEGATIVE_AMORTIZATION,
                RiskCode.NET_WORTH_DECREASE, RiskCode.LIQUID_ASSET_DECREASE);
    }

    @Test
    void detectsGoalWarningCodesAndNotAchievableStatus() {
        GoalAgentToolResult result = new GoalAgentToolResult("TARGET_NET_WORTH", money("100"),
                YearMonth.of(2026, 8), YearMonth.of(2027, 7), 12, "NOT_ACHIEVABLE",
                money("50"), money("50"), null, List.of(), List.of("EXPENSE_REDUCTION_INFEASIBLE",
                "INVESTMENT_CONTRIBUTION_CASH_LIMITED"));

        assertThat(checker.check(result)).extracting(RiskFinding::code).containsExactly(
                RiskCode.GOAL_NOT_ACHIEVED, RiskCode.EXPENSE_REDUCTION_INFEASIBLE,
                RiskCode.INVESTMENT_CONTRIBUTION_CASH_LIMITED);
    }

    @Test
    void doesNotInventRiskForNormalResult() {
        BaselineAgentToolResult result = new BaselineAgentToolResult(YearMonth.of(2026, 8), 12,
                YearMonth.of(2027, 7), money("100"), money("100"), BigDecimal.ZERO, money("200"),
                List.of(), List.of());
        assertThat(checker.check(result)).isEmpty();
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
