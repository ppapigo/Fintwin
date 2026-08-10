package com.fintwin.fintwin.agent.risk;

import com.fintwin.fintwin.agent.domain.AgentToolResult;
import com.fintwin.fintwin.agent.domain.BaselineAgentToolResult;
import com.fintwin.fintwin.agent.domain.GoalAgentToolResult;
import com.fintwin.fintwin.agent.domain.RiskCode;
import com.fintwin.fintwin.agent.domain.RiskFinding;
import com.fintwin.fintwin.agent.domain.RiskSeverity;
import com.fintwin.fintwin.agent.domain.ScenarioAgentToolResult;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Component
public final class DeterministicRiskChecker {
    public List<RiskFinding> check(AgentToolResult result) {
        List<RiskFinding> findings = new ArrayList<>();
        switch (result) {
            case BaselineAgentToolResult baseline -> addSimulationRisks(
                    baseline.cashShortfallMonths(), baseline.negativeAmortizationMonths(), findings);
            case ScenarioAgentToolResult scenario -> {
                addSimulationRisks(scenario.cashShortfallMonths(), scenario.negativeAmortizationMonths(), findings);
                if (scenario.netWorthDelta().signum() < 0) {
                    findings.add(new RiskFinding(RiskCode.NET_WORTH_DECREASE, RiskSeverity.WARNING,
                            "typedResult.netWorthDelta", null,
                            "What-if 결과의 최종 순자산이 기준안보다 감소했습니다."));
                }
                if (scenario.liquidAssetsDelta().signum() < 0) {
                    findings.add(new RiskFinding(RiskCode.LIQUID_ASSET_DECREASE, RiskSeverity.INFO,
                            "typedResult.liquidAssetsDelta", null,
                            "What-if 결과의 최종 유동자산이 기준안보다 감소했습니다."));
                }
            }
            case GoalAgentToolResult goal -> addGoalRisks(goal, findings);
        }
        return List.copyOf(findings);
    }

    private void addSimulationRisks(List<YearMonth> cashShortfalls, List<YearMonth> negativeAmortizations,
                                    List<RiskFinding> findings) {
        cashShortfalls.forEach(yearMonth -> findings.add(new RiskFinding(RiskCode.CASH_SHORTFALL,
                RiskSeverity.HIGH, "typedResult.cashShortfallMonths", yearMonth,
                "해당 월에 현금 부족이 발생했습니다.")));
        negativeAmortizations.forEach(yearMonth -> findings.add(new RiskFinding(RiskCode.NEGATIVE_AMORTIZATION,
                RiskSeverity.WARNING, "typedResult.negativeAmortizationMonths", yearMonth,
                "해당 월에 대출 원금이 감소하지 않는 음의 상환이 발생했습니다.")));
    }

    private void addGoalRisks(GoalAgentToolResult goal, List<RiskFinding> findings) {
        if ("NOT_ACHIEVABLE".equals(goal.goalStatus())) {
            findings.add(new RiskFinding(RiskCode.GOAL_NOT_ACHIEVED, RiskSeverity.WARNING,
                    "typedResult.goalStatus", null, "검증된 탐색 범위에서 목표 달성 대안을 찾지 못했습니다."));
        }
        if (goal.warningCodes().contains("CASH_SHORTFALL")) {
            findings.add(new RiskFinding(RiskCode.CASH_SHORTFALL, RiskSeverity.HIGH,
                    "typedResult.warningCodes", null, "목표 역산 결과에 현금 부족 경고가 포함됐습니다."));
        }
        if (goal.warningCodes().contains("NEGATIVE_AMORTIZATION")) {
            findings.add(new RiskFinding(RiskCode.NEGATIVE_AMORTIZATION, RiskSeverity.WARNING,
                    "typedResult.warningCodes", null, "목표 역산 결과에 음의 상환 경고가 포함됐습니다."));
        }
        if (goal.warningCodes().contains("EXPENSE_REDUCTION_INFEASIBLE")) {
            findings.add(new RiskFinding(RiskCode.EXPENSE_REDUCTION_INFEASIBLE, RiskSeverity.WARNING,
                    "typedResult.warningCodes", null, "변동지출 절감 제약 안에서 해당 대안은 실행 불가로 판정됐습니다."));
        }
        if (goal.warningCodes().contains("INVESTMENT_CONTRIBUTION_CASH_LIMITED")) {
            findings.add(new RiskFinding(RiskCode.INVESTMENT_CONTRIBUTION_CASH_LIMITED, RiskSeverity.WARNING,
                    "typedResult.warningCodes", null, "일부 월의 투자 납입액이 가용 현금으로 제한됐습니다."));
        }
    }
}
