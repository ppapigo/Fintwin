package com.fintwin.fintwin.agent.domain;

import com.fintwin.fintwin.scenario.dto.ScenarioComparisonResponse;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * Privacy-safe projection of a completed scenario comparison.
 *
 * <p>The projection deliberately excludes the financial profile identifier and copies only immutable,
 * strongly typed calculation results that are safe to return to the authenticated caller. It is created from
 * the result already produced by the primary scenario tool and never triggers another simulation.</p>
 */
public record ScenarioComparisonDetails(
        int financialProfileVersion,
        YearMonth startYearMonth,
        int horizonMonths,
        BaselineSimulationResponse.AssumptionsResponse assumptions,
        List<ScenarioComparisonResponse.NormalizedEventResponse> normalizedEvents,
        SimulationResult baseline,
        SimulationResult whatIf,
        List<ScenarioComparisonResponse.ComparisonResult> checkpointComparisons,
        ScenarioComparisonResponse.ComparisonResult finalComparison,
        ScenarioComparisonResponse.ImpactSummary impactSummary,
        List<String> calculationWarnings
) {
    public ScenarioComparisonDetails {
        Objects.requireNonNull(startYearMonth);
        Objects.requireNonNull(assumptions);
        Objects.requireNonNull(baseline);
        Objects.requireNonNull(whatIf);
        Objects.requireNonNull(finalComparison);
        Objects.requireNonNull(impactSummary);
        normalizedEvents = List.copyOf(normalizedEvents);
        checkpointComparisons = List.copyOf(checkpointComparisons);
        calculationWarnings = List.copyOf(calculationWarnings);
    }

    public static ScenarioComparisonDetails from(ScenarioComparisonResponse response) {
        Objects.requireNonNull(response);
        return new ScenarioComparisonDetails(response.financialProfileVersion(), response.startYearMonth(),
                response.horizonMonths(), response.assumptions(), response.normalizedEvents(),
                SimulationResult.from(response.baseline()), SimulationResult.from(response.whatIf()),
                response.checkpointComparisons(), response.finalComparison(), response.impactSummary(),
                response.warnings());
    }

    public record SimulationResult(
            List<BaselineSimulationResponse.MonthlyResultResponse> monthlyResults,
            List<BaselineSimulationResponse.CheckpointResponse> checkpoints,
            BaselineSimulationResponse.CumulativeTotalsResponse finalCumulativeTotals,
            BaselineSimulationResponse.CalculationBasisResponse calculationBasis
    ) {
        public SimulationResult {
            monthlyResults = List.copyOf(monthlyResults);
            checkpoints = List.copyOf(checkpoints);
            Objects.requireNonNull(finalCumulativeTotals);
            Objects.requireNonNull(calculationBasis);
        }

        private static SimulationResult from(BaselineSimulationResponse response) {
            return new SimulationResult(response.monthlyResults(), response.checkpoints(),
                    response.finalCumulativeTotals(), response.calculationBasis());
        }
    }
}
