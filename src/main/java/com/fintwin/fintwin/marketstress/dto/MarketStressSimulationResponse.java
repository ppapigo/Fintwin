package com.fintwin.fintwin.marketstress.dto;

import com.fintwin.fintwin.marketstress.domain.GoalMarginComparison;
import com.fintwin.fintwin.marketstress.domain.GoalMarginStatus;
import com.fintwin.fintwin.marketstress.domain.MarketExposure;
import com.fintwin.fintwin.marketstress.domain.MarketImpactBreakdown;
import com.fintwin.fintwin.marketstress.domain.MarketStressResult;
import com.fintwin.fintwin.marketstress.domain.MarketStressScenario;
import com.fintwin.fintwin.marketstress.domain.MarketStressWarning;
import com.fintwin.fintwin.marketstress.domain.MarketStressWarningCode;
import com.fintwin.fintwin.marketstress.domain.RiskComparison;
import com.fintwin.fintwin.marketstress.domain.RiskSnapshot;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MarketStressSimulationResponse(
        int financialProfileVersion,
        YearMonth startYearMonth,
        int horizonMonths,
        AssumptionsResponse assumptions,
        ExposureResponse exposure,
        StressScenarioResponse stressScenario,
        MarketContextUsageResponse marketContextUsage,
        SimulationRunResponse baseline,
        SimulationRunResponse stressed,
        MarketImpactBreakdownResponse marketImpactBreakdown,
        RiskComparisonResponse riskComparison,
        GoalMarginComparisonResponse goalMarginComparison,
        List<WarningResponse> warnings,
        CalculationBasisResponse calculationBasis,
        String disclaimer
) {
    public static MarketStressSimulationResponse from(int profileVersion, BigDecimal profileInvestmentAssets,
                                                      YearMonth startYearMonth, int horizonMonths,
                                                      SimulationAssumptions assumptions, MarketExposure exposure,
                                                      MarketStressScenario scenario, MarketStressResult result) {
        return new MarketStressSimulationResponse(profileVersion, startYearMonth, horizonMonths,
                AssumptionsResponse.from(assumptions), ExposureResponse.from(exposure, profileInvestmentAssets),
                StressScenarioResponse.from(scenario), MarketContextUsageResponse.separate(),
                SimulationRunResponse.from(result.baseline()), SimulationRunResponse.from(result.stressed()),
                MarketImpactBreakdownResponse.from(result.marketImpactBreakdown()),
                RiskComparisonResponse.from(result.riskComparison()),
                GoalMarginComparisonResponse.from(result.goalMarginComparison()),
                result.warnings().stream().map(WarningResponse::from).toList(),
                CalculationBasisResponse.standard(),
                "This deterministic stress test uses only explicit user assumptions. It is not a forecast, guarantee, financial-product recommendation, or investment advice.");
    }

    public record AssumptionsResponse(BigDecimal annualIncomeGrowthRate, BigDecimal annualInflationRate,
                                      BigDecimal annualDepositInterestRate, BigDecimal annualInvestmentReturnRate,
                                      BigDecimal monthlyDebtPayment) {
        static AssumptionsResponse from(SimulationAssumptions assumptions) {
            return new AssumptionsResponse(assumptions.annualIncomeGrowthRate(), assumptions.annualInflationRate(),
                    assumptions.annualDepositInterestRate(), assumptions.annualInvestmentReturnRate(),
                    assumptions.monthlyDebtPayment());
        }
    }

    public record ExposureResponse(BigDecimal domesticStockAmount, BigDecimal overseasStockAmount,
                                   BigDecimal otherInvestmentAssets) {
        static ExposureResponse from(MarketExposure exposure, BigDecimal profileInvestmentAssets) {
            return new ExposureResponse(exposure.domesticStockAmount(), exposure.overseasStockAmount(),
                    profileInvestmentAssets.subtract(exposure.totalStockExposure()));
        }
    }

    public record StressScenarioResponse(YearMonth shockYearMonth, BigDecimal domesticStockShockRate,
                                         BigDecimal overseasStockShockRate,
                                         BigDecimal krwUsdExchangeRateShockRate,
                                         BigDecimal loanInterestRateChangePercentagePoints) {
        static StressScenarioResponse from(MarketStressScenario scenario) {
            return new StressScenarioResponse(scenario.shockYearMonth(), scenario.domesticStockShockRate(),
                    scenario.overseasStockShockRate(), scenario.krwUsdExchangeRateShockRate(),
                    scenario.loanInterestRateChangePercentagePoints());
        }
    }

    public record MarketContextUsageResponse(boolean usedInCalculation, String contextEndpoint, String boundary) {
        static MarketContextUsageResponse separate() {
            return new MarketContextUsageResponse(false, "/api/market-stress/context",
                    "Current observations are display-only context; the engine uses only submitted stress assumptions.");
        }
    }

    public record SimulationRunResponse(
            List<BaselineSimulationResponse.MonthlyResultResponse> monthlyResults,
            List<BaselineSimulationResponse.CheckpointResponse> checkpoints,
            BaselineSimulationResponse.CumulativeTotalsResponse finalCumulativeTotals
    ) {
        static SimulationRunResponse from(SimulationSummary summary) {
            return new SimulationRunResponse(
                    summary.monthlyResults().stream().map(BaselineSimulationResponse.MonthlyResultResponse::from)
                            .toList(),
                    summary.checkpoints().stream().map(BaselineSimulationResponse.CheckpointResponse::from).toList(),
                    BaselineSimulationResponse.CumulativeTotalsResponse.from(summary.finalCumulativeTotals()));
        }
    }

    public record MarketImpactBreakdownResponse(YearMonth shockYearMonth, BigDecimal domesticExposureAtShock,
                                                BigDecimal domesticStockImpact,
                                                BigDecimal overseasExposureAtShock,
                                                BigDecimal overseasStockImpact,
                                                BigDecimal exchangeRateImpact,
                                                BigDecimal totalInvestmentImpact,
                                                BigDecimal additionalDebtInterest,
                                                BigDecimal finalNetWorthDelta) {
        static MarketImpactBreakdownResponse from(MarketImpactBreakdown value) {
            return new MarketImpactBreakdownResponse(value.shockYearMonth(), value.domesticExposureAtShock(),
                    value.domesticStockImpact(), value.overseasExposureAtShock(), value.overseasStockImpact(),
                    value.exchangeRateImpact(), value.totalInvestmentImpact(), value.additionalDebtInterest(),
                    value.finalNetWorthDelta());
        }
    }

    public record RiskSnapshotResponse(boolean cashShortfall, int cashShortfallMonthCount,
                                       YearMonth firstCashShortfallMonth, boolean negativeAmortization,
                                       int negativeAmortizationMonthCount, YearMonth firstNegativeAmortizationMonth,
                                       BigDecimal minimumLiquidAssets, BigDecimal finalRemainingDebt) {
        static RiskSnapshotResponse from(RiskSnapshot value) {
            return new RiskSnapshotResponse(value.cashShortfall(), value.cashShortfallMonthCount(),
                    value.firstCashShortfallMonth(), value.negativeAmortization(),
                    value.negativeAmortizationMonthCount(), value.firstNegativeAmortizationMonth(),
                    value.minimumLiquidAssets(), value.finalRemainingDebt());
        }
    }

    public record RiskComparisonResponse(RiskSnapshotResponse baseline, RiskSnapshotResponse stressed,
                                         boolean newCashShortfall, boolean newNegativeAmortization) {
        static RiskComparisonResponse from(RiskComparison value) {
            return new RiskComparisonResponse(RiskSnapshotResponse.from(value.baseline()),
                    RiskSnapshotResponse.from(value.stressed()), value.newCashShortfall(),
                    value.newNegativeAmortization());
        }
    }

    public record GoalMarginComparisonResponse(GoalMarginStatus status, BigDecimal targetNetWorth,
                                               BigDecimal baselineFinalNetWorth, BigDecimal stressedFinalNetWorth,
                                               BigDecimal baselineMargin, BigDecimal stressedMargin,
                                               BigDecimal marginDelta) {
        static GoalMarginComparisonResponse from(GoalMarginComparison value) {
            return new GoalMarginComparisonResponse(value.status(), value.targetNetWorth(),
                    value.baselineFinalNetWorth(), value.stressedFinalNetWorth(), value.baselineMargin(),
                    value.stressedMargin(), value.marginDelta());
        }
    }

    public record WarningResponse(MarketStressWarningCode code, String message) {
        static WarningResponse from(MarketStressWarning warning) {
            return new WarningResponse(warning.code(), warning.message());
        }
    }

    public record CalculationBasisResponse(String marketShockOrder, String loanRateTreatment,
                                           String exposureTreatment, String moneyRounding,
                                           String currentMarketDataTreatment) {
        static CalculationBasisResponse standard() {
            return new CalculationBasisResponse(
                    "general investment return, domestic/overseas market shock, then FX shock on overseas exposure",
                    "percentage-point change applies from shock month through the end; effective rate is floored at zero",
                    "only submitted current domestic/overseas exposure is shocked; future contributions remain unclassified",
                    "2 decimals, HALF_UP",
                    "official observations are displayed separately and never become forecasts or calculation inputs");
        }
    }
}
