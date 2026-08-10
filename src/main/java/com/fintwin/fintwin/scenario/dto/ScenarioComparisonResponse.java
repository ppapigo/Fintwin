package com.fintwin.fintwin.scenario.dto;

import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.domain.ScenarioDefinition;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record ScenarioComparisonResponse(
        Long financialProfileId,
        int financialProfileVersion,
        String scenarioName,
        YearMonth startYearMonth,
        int horizonMonths,
        BaselineSimulationResponse.AssumptionsResponse assumptions,
        List<NormalizedEventResponse> normalizedEvents,
        BaselineSimulationResponse baseline,
        BaselineSimulationResponse whatIf,
        List<ComparisonResult> checkpointComparisons,
        ComparisonResult finalComparison,
        ImpactSummary impactSummary,
        List<String> warnings
) {
    public static ScenarioComparisonResponse from(Long profileId, int profileVersion, YearMonth startYearMonth,
                                                  int horizonMonths, SimulationAssumptions assumptions,
                                                  ScenarioDefinition scenario, SimulationSummary baselineSummary,
                                                  SimulationSummary whatIfSummary, List<String> warnings) {
        BaselineSimulationResponse baseline = BaselineSimulationResponse.from(profileId, profileVersion,
                startYearMonth, horizonMonths, assumptions, baselineSummary);
        BaselineSimulationResponse whatIf = BaselineSimulationResponse.from(profileId, profileVersion,
                startYearMonth, horizonMonths, assumptions, whatIfSummary);
        List<ComparisonResult> checkpoints = buildCheckpointComparisons(baseline, whatIf);
        ComparisonResult finalComparison = ComparisonResult.between(
                baseline.monthlyResults().getLast(), whatIf.monthlyResults().getLast());
        return new ScenarioComparisonResponse(profileId, profileVersion, scenario.name(), startYearMonth,
                horizonMonths, baseline.assumptions(), scenario.events().stream().map(NormalizedEventResponse::from)
                .toList(), baseline, whatIf, checkpoints, finalComparison,
                ImpactSummary.from(finalComparison), List.copyOf(warnings));
    }

    private static List<ComparisonResult> buildCheckpointComparisons(BaselineSimulationResponse baseline,
                                                                      BaselineSimulationResponse whatIf) {
        return baseline.checkpoints().stream().map(baselineCheckpoint -> {
            BaselineSimulationResponse.CheckpointResponse whatIfCheckpoint = whatIf.checkpoints().stream()
                    .filter(candidate -> candidate.monthNumber() == baselineCheckpoint.monthNumber())
                    .findFirst().orElseThrow();
            return ComparisonResult.between(baselineCheckpoint, whatIfCheckpoint);
        }).toList();
    }

    public record NormalizedEventResponse(
            String eventId,
            String eventType,
            YearMonth effectiveYearMonth,
            YearMonth startYearMonth,
            YearMonth endYearMonth,
            BigDecimal amount,
            BigDecimal monthlyDelta,
            String description
    ) {
        static NormalizedEventResponse from(FinancialEvent event) {
            return switch (event) {
                case FinancialEvent.OneTimeExpense oneTime -> new NormalizedEventResponse(oneTime.eventId(),
                        oneTime.eventType().name(), oneTime.effectiveYearMonth(), null, null, oneTime.amount(), null,
                        oneTime.description());
                case FinancialEvent.ExtraDebtRepayment repayment -> new NormalizedEventResponse(
                        repayment.eventId(), repayment.eventType().name(), repayment.effectiveYearMonth(), null, null,
                        repayment.amount(), null, repayment.description());
                case FinancialEvent.RecurringExpenseChange recurring -> period(recurring.eventId(),
                        recurring.eventType().name(), recurring.startYearMonth(), recurring.endYearMonth(),
                        recurring.monthlyDelta(), recurring.description());
                case FinancialEvent.IncomeChange income -> period(income.eventId(), income.eventType().name(),
                        income.startYearMonth(), income.endYearMonth(), income.monthlyDelta(), income.description());
                case FinancialEvent.IncomePause pause -> period(pause.eventId(), pause.eventType().name(),
                        pause.startYearMonth(), pause.endYearMonth(), null, pause.description());
                case FinancialEvent.InvestmentContributionChange investment -> period(investment.eventId(),
                        investment.eventType().name(), investment.startYearMonth(), investment.endYearMonth(),
                        investment.monthlyDelta(), investment.description());
            };
        }

        private static NormalizedEventResponse period(String id, String type, YearMonth start, YearMonth end,
                                                      BigDecimal delta, String description) {
            return new NormalizedEventResponse(id, type, null, start, end, null, delta, description);
        }
    }

    public record ComparisonResult(
            int monthNumber,
            YearMonth yearMonth,
            BigDecimal liquidAssetsDelta,
            BigDecimal investmentAssetsDelta,
            BigDecimal totalFinancialAssetsDelta,
            BigDecimal debtDelta,
            BigDecimal netWorthDelta,
            BigDecimal cumulativeIncomeDelta,
            BigDecimal cumulativeConsumptionDelta,
            BigDecimal cumulativeDebtInterestDelta,
            BigDecimal cumulativePrincipalRepaidDelta,
            BigDecimal cumulativeInvestmentContributionDelta,
            BigDecimal cumulativeInvestmentReturnDelta
    ) {
        static ComparisonResult between(BaselineSimulationResponse.MonthlyResultResponse baseline,
                                        BaselineSimulationResponse.MonthlyResultResponse whatIf) {
            return new ComparisonResult(baseline.monthNumber(), baseline.yearMonth(),
                    delta(whatIf.liquidAssets(), baseline.liquidAssets()),
                    delta(whatIf.investmentAssets(), baseline.investmentAssets()),
                    delta(whatIf.totalFinancialAssets(), baseline.totalFinancialAssets()),
                    delta(whatIf.remainingDebt(), baseline.remainingDebt()),
                    delta(whatIf.netWorth(), baseline.netWorth()),
                    delta(whatIf.cumulativeTotals().income(), baseline.cumulativeTotals().income()),
                    delta(whatIf.cumulativeTotals().consumption(), baseline.cumulativeTotals().consumption()),
                    delta(whatIf.cumulativeTotals().debtInterest(), baseline.cumulativeTotals().debtInterest()),
                    delta(whatIf.cumulativeTotals().principalRepaid(),
                            baseline.cumulativeTotals().principalRepaid()),
                    delta(whatIf.cumulativeTotals().investmentContributions(),
                            baseline.cumulativeTotals().investmentContributions()),
                    delta(whatIf.cumulativeTotals().investmentReturn(),
                            baseline.cumulativeTotals().investmentReturn()));
        }

        static ComparisonResult between(BaselineSimulationResponse.CheckpointResponse baseline,
                                        BaselineSimulationResponse.CheckpointResponse whatIf) {
            return new ComparisonResult(baseline.monthNumber(), baseline.yearMonth(),
                    delta(whatIf.liquidAssets(), baseline.liquidAssets()),
                    delta(whatIf.investmentAssets(), baseline.investmentAssets()),
                    delta(whatIf.totalFinancialAssets(), baseline.totalFinancialAssets()),
                    delta(whatIf.remainingDebt(), baseline.remainingDebt()),
                    delta(whatIf.netWorth(), baseline.netWorth()),
                    delta(whatIf.cumulativeTotals().income(), baseline.cumulativeTotals().income()),
                    delta(whatIf.cumulativeTotals().consumption(), baseline.cumulativeTotals().consumption()),
                    delta(whatIf.cumulativeTotals().debtInterest(), baseline.cumulativeTotals().debtInterest()),
                    delta(whatIf.cumulativeTotals().principalRepaid(),
                            baseline.cumulativeTotals().principalRepaid()),
                    delta(whatIf.cumulativeTotals().investmentContributions(),
                            baseline.cumulativeTotals().investmentContributions()),
                    delta(whatIf.cumulativeTotals().investmentReturn(),
                            baseline.cumulativeTotals().investmentReturn()));
        }

        private static BigDecimal delta(BigDecimal whatIf, BigDecimal baseline) {
            return whatIf.subtract(baseline);
        }
    }

    public record ImpactSummary(
            BigDecimal incomeDelta,
            BigDecimal consumptionDelta,
            BigDecimal debtInterestDelta,
            BigDecimal principalRepaidDelta,
            BigDecimal investmentContributionDelta,
            BigDecimal investmentReturnDelta,
            BigDecimal liquidAssetsDelta,
            BigDecimal debtDelta,
            BigDecimal netWorthDelta,
            BigDecimal residualDelta
    ) {
        static ImpactSummary from(ComparisonResult result) {
            BigDecimal explainedNetWorthDelta = result.cumulativeIncomeDelta()
                    .subtract(result.cumulativeConsumptionDelta())
                    .subtract(result.cumulativeDebtInterestDelta())
                    .add(result.cumulativeInvestmentReturnDelta());
            BigDecimal residual = result.netWorthDelta().subtract(explainedNetWorthDelta);
            return new ImpactSummary(result.cumulativeIncomeDelta(), result.cumulativeConsumptionDelta(),
                    result.cumulativeDebtInterestDelta(), result.cumulativePrincipalRepaidDelta(),
                    result.cumulativeInvestmentContributionDelta(), result.cumulativeInvestmentReturnDelta(),
                    result.liquidAssetsDelta(), result.debtDelta(), result.netWorthDelta(), residual);
        }
    }
}
