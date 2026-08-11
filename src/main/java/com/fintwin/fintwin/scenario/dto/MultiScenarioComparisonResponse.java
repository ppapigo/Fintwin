package com.fintwin.fintwin.scenario.dto;

import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.scenario.domain.ScenarioRiskFinding;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public record MultiScenarioComparisonResponse(
        int financialProfileVersion,
        YearMonth startYearMonth,
        int horizonMonths,
        BaselineSimulationResponse.AssumptionsResponse assumptions,
        SimulationResult baseline,
        List<ScenarioResult> scenarios,
        List<CheckpointComparison> checkpointComparisons,
        ComparisonSummary comparisonSummary,
        List<WarningResponse> calculationWarnings,
        BaselineSimulationResponse.CalculationBasisResponse calculationBasis,
        String disclaimer
) {
    private static final String DISCLAIMER = "This is a deterministic simulation under user-provided assumptions, "
            + "not financial advice, a return forecast, or a guarantee of future results.";

    public static MultiScenarioComparisonResponse from(BaselineSimulationResponse baseline,
                                                       List<ScenarioResult> scenarios,
                                                       List<ScenarioRiskFinding> baselineRisks) {
        SimulationResult baselineResult = SimulationResult.from(baseline);
        List<WarningResponse> warnings = new ArrayList<>();
        baselineRisks.stream().map(WarningResponse::forBaseline).forEach(warnings::add);
        scenarios.stream().flatMap(result -> result.warnings().stream()).forEach(warnings::add);
        return new MultiScenarioComparisonResponse(baseline.financialProfileVersion(), baseline.startYearMonth(),
                baseline.horizonMonths(), baseline.assumptions(), baselineResult, List.copyOf(scenarios),
                checkpointComparisons(baseline, scenarios), ComparisonSummary.from(baselineResult, scenarios),
                List.copyOf(warnings), baseline.calculationBasis(), DISCLAIMER);
    }

    private static List<CheckpointComparison> checkpointComparisons(BaselineSimulationResponse baseline,
                                                                    List<ScenarioResult> scenarios) {
        return baseline.checkpoints().stream().map(baselineCheckpoint -> {
            List<ScenarioCheckpoint> scenarioCheckpoints = scenarios.stream().map(scenario -> {
                BaselineSimulationResponse.CheckpointResponse checkpoint = scenario.checkpoints().stream()
                        .filter(candidate -> candidate.monthNumber() == baselineCheckpoint.monthNumber())
                        .findFirst().orElseThrow();
                return new ScenarioCheckpoint(scenario.scenarioKey(), scenario.label(), checkpoint,
                        ScenarioComparisonResponse.ComparisonResult.between(baselineCheckpoint, checkpoint));
            }).toList();
            return new CheckpointComparison(baselineCheckpoint.monthNumber(), baselineCheckpoint.yearMonth(),
                    baselineCheckpoint, scenarioCheckpoints);
        }).toList();
    }

    public record SimulationResult(
            List<BaselineSimulationResponse.MonthlyResultResponse> monthlyResults,
            List<BaselineSimulationResponse.CheckpointResponse> checkpoints,
            BigDecimal finalLiquidAssets,
            BigDecimal finalInvestmentAssets,
            BigDecimal finalTotalFinancialAssets,
            BigDecimal finalDebt,
            BigDecimal finalNetWorth,
            BigDecimal lastMonthDisposableCashFlow,
            BaselineSimulationResponse.CumulativeTotalsResponse finalCumulativeTotals,
            boolean cashShortfall,
            boolean negativeAmortization
    ) {
        static SimulationResult from(BaselineSimulationResponse response) {
            BaselineSimulationResponse.MonthlyResultResponse last = response.monthlyResults().getLast();
            return new SimulationResult(List.copyOf(response.monthlyResults()), List.copyOf(response.checkpoints()),
                    last.liquidAssets(), last.investmentAssets(), last.totalFinancialAssets(), last.remainingDebt(),
                    last.netWorth(), last.disposableCashFlow(), response.finalCumulativeTotals(),
                    response.monthlyResults().stream().anyMatch(
                            BaselineSimulationResponse.MonthlyResultResponse::cashShortfall),
                    response.monthlyResults().stream().anyMatch(
                            BaselineSimulationResponse.MonthlyResultResponse::negativeAmortization));
        }
    }

    public record ScenarioResult(
            String scenarioKey,
            String label,
            List<ScenarioComparisonResponse.NormalizedEventResponse> normalizedEvents,
            List<BaselineSimulationResponse.MonthlyResultResponse> monthlyResults,
            List<BaselineSimulationResponse.CheckpointResponse> checkpoints,
            BigDecimal finalLiquidAssets,
            BigDecimal finalInvestmentAssets,
            BigDecimal finalTotalFinancialAssets,
            BigDecimal finalDebt,
            BigDecimal finalNetWorth,
            BigDecimal lastMonthDisposableCashFlow,
            BaselineSimulationResponse.CumulativeTotalsResponse finalCumulativeTotals,
            ScenarioComparisonResponse.ComparisonResult baselineDelta,
            BigDecimal residualDelta,
            boolean cashShortfall,
            boolean negativeAmortization,
            List<WarningResponse> warnings
    ) {
        public static ScenarioResult from(String scenarioKey, String label, ScenarioDefinition scenario,
                                          BaselineSimulationResponse baseline,
                                          BaselineSimulationResponse simulated,
                                          List<ScenarioRiskFinding> risks) {
            BaselineSimulationResponse.MonthlyResultResponse last = simulated.monthlyResults().getLast();
            ScenarioComparisonResponse.ComparisonResult delta =
                    ScenarioComparisonResponse.ComparisonResult.between(
                            baseline.monthlyResults().getLast(), last);
            BigDecimal residual = ScenarioComparisonResponse.ImpactSummary.from(delta).residualDelta();
            return new ScenarioResult(scenarioKey, label,
                    scenario.events().stream().map(ScenarioComparisonResponse.NormalizedEventResponse::from).toList(),
                    List.copyOf(simulated.monthlyResults()), List.copyOf(simulated.checkpoints()),
                    last.liquidAssets(), last.investmentAssets(), last.totalFinancialAssets(), last.remainingDebt(),
                    last.netWorth(), last.disposableCashFlow(), simulated.finalCumulativeTotals(), delta, residual,
                    simulated.monthlyResults().stream().anyMatch(
                            BaselineSimulationResponse.MonthlyResultResponse::cashShortfall),
                    simulated.monthlyResults().stream().anyMatch(
                            BaselineSimulationResponse.MonthlyResultResponse::negativeAmortization),
                    risks.stream().map(risk -> WarningResponse.forScenario(scenarioKey, risk)).toList());
        }
    }

    public record CheckpointComparison(
            int monthNumber,
            YearMonth yearMonth,
            BaselineSimulationResponse.CheckpointResponse baseline,
            List<ScenarioCheckpoint> scenarios
    ) {
    }

    public record ScenarioCheckpoint(
            String scenarioKey,
            String label,
            BaselineSimulationResponse.CheckpointResponse result,
            ScenarioComparisonResponse.ComparisonResult baselineDelta
    ) {
    }

    public record ComparisonSummary(
            SummaryItem baseline,
            List<SummaryItem> scenarios
    ) {
        static ComparisonSummary from(SimulationResult baseline, List<ScenarioResult> scenarios) {
            SummaryItem baselineItem = new SummaryItem("A", "현재 생활 유지", baseline.finalLiquidAssets(),
                    baseline.finalInvestmentAssets(), baseline.finalTotalFinancialAssets(), baseline.finalDebt(),
                    baseline.finalNetWorth(), baseline.lastMonthDisposableCashFlow(), null);
            List<SummaryItem> scenarioItems = scenarios.stream().map(result -> new SummaryItem(
                    result.scenarioKey(), result.label(), result.finalLiquidAssets(), result.finalInvestmentAssets(),
                    result.finalTotalFinancialAssets(), result.finalDebt(), result.finalNetWorth(),
                    result.lastMonthDisposableCashFlow(), result.baselineDelta())).toList();
            return new ComparisonSummary(baselineItem, scenarioItems);
        }
    }

    public record SummaryItem(
            String scenarioKey,
            String label,
            BigDecimal finalLiquidAssets,
            BigDecimal finalInvestmentAssets,
            BigDecimal finalTotalFinancialAssets,
            BigDecimal finalDebt,
            BigDecimal finalNetWorth,
            BigDecimal lastMonthDisposableCashFlow,
            ScenarioComparisonResponse.ComparisonResult baselineDelta
    ) {
    }

    public record WarningResponse(
            String scope,
            String scenarioKey,
            String code,
            String message,
            YearMonth affectedYearMonth
    ) {
        static WarningResponse forBaseline(ScenarioRiskFinding finding) {
            return new WarningResponse("BASELINE", null, finding.code(), finding.message(),
                    finding.affectedYearMonth());
        }

        static WarningResponse forScenario(String scenarioKey, ScenarioRiskFinding finding) {
            return new WarningResponse("SCENARIO", scenarioKey, finding.code(), finding.message(),
                    finding.affectedYearMonth());
        }
    }
}
