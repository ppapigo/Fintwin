package com.fintwin.fintwin.scenario.risk;

import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.domain.ScenarioRiskFinding;
import com.fintwin.fintwin.scenario.engine.ScenarioMonthlyAdjustmentProvider;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ScenarioResultRiskChecker {
    public List<ScenarioRiskFinding> baselineRisks(SimulationSummary baseline) {
        List<ScenarioRiskFinding> findings = new ArrayList<>();
        addFirstResultRisk(baseline, MonthlySimulationResult::cashShortfall, "CASH_SHORTFALL",
                "At least one simulated month has a cash shortfall.", findings);
        addFirstResultRisk(baseline, MonthlySimulationResult::negativeAmortization, "NEGATIVE_AMORTIZATION",
                "At least one simulated month has negative amortization.", findings);
        return List.copyOf(findings);
    }

    public List<ScenarioRiskFinding> scenarioRisks(SimulationInput input, ScenarioDefinition scenario,
                                                   ScenarioMonthlyAdjustmentProvider adjustmentProvider,
                                                   SimulationSummary baseline, SimulationSummary simulated) {
        List<ScenarioRiskFinding> findings = new ArrayList<>();
        addFirstResultRisk(simulated, MonthlySimulationResult::cashShortfall, "CASH_SHORTFALL",
                "At least one simulated month has a cash shortfall.", findings);
        addFirstResultRisk(simulated, MonthlySimulationResult::negativeAmortization, "NEGATIVE_AMORTIZATION",
                "At least one simulated month has negative amortization.", findings);

        MonthlySimulationResult baselineLast = baseline.monthlyResults().getLast();
        MonthlySimulationResult scenarioLast = simulated.monthlyResults().getLast();
        if (scenarioLast.netWorth().compareTo(baselineLast.netWorth()) < 0) {
            findings.add(new ScenarioRiskFinding("NET_WORTH_BELOW_BASELINE",
                    "Final net worth is lower than the baseline result.", scenarioLast.yearMonth()));
        }
        if (scenarioLast.liquidAssets().compareTo(baselineLast.liquidAssets()) < 0) {
            findings.add(new ScenarioRiskFinding("LIQUID_ASSETS_BELOW_BASELINE",
                    "Final liquid assets are lower than the baseline result.", scenarioLast.yearMonth()));
        }
        if (scenarioLast.remainingDebt().compareTo(baselineLast.remainingDebt()) > 0) {
            findings.add(new ScenarioRiskFinding("DEBT_ABOVE_BASELINE",
                    "Final debt is higher than the baseline result.", scenarioLast.yearMonth()));
        }

        simulated.monthlyResults().stream().filter(result -> {
            BigDecimal requested = input.plannedMonthlyInvestment()
                    .add(adjustmentProvider.adjustmentsFor(result.yearMonth()).investmentContributionDelta())
                    .max(BigDecimal.ZERO);
            return requested.compareTo(result.investmentContribution()) > 0;
        }).findFirst().ifPresent(result -> findings.add(new ScenarioRiskFinding(
                "INVESTMENT_CONTRIBUTION_CASH_LIMITED",
                "A planned investment contribution was limited by available cash.", result.yearMonth())));

        simulated.monthlyResults().stream().filter(result -> {
            BigDecimal requested = adjustmentProvider.adjustmentsFor(result.yearMonth()).extraDebtRepayment();
            return requested.compareTo(result.extraDebtRepayment()) > 0;
        }).findFirst().ifPresent(result -> findings.add(new ScenarioRiskFinding(
                "EXTRA_DEBT_REPAYMENT_LIMITED",
                "An extra debt repayment was limited by remaining debt or available cash.", result.yearMonth())));

        if (!scenario.warnings().isEmpty()) {
            findings.add(new ScenarioRiskFinding("EVENT_PERIOD_CLIPPED",
                    "At least one event period was clipped to the simulation range.", null));
        }
        return List.copyOf(findings);
    }

    private void addFirstResultRisk(SimulationSummary summary, Predicate<MonthlySimulationResult> predicate,
                                    String code, String message, List<ScenarioRiskFinding> findings) {
        summary.monthlyResults().stream().filter(predicate).findFirst()
                .ifPresent(result -> findings.add(new ScenarioRiskFinding(code, message, result.yearMonth())));
    }
}
