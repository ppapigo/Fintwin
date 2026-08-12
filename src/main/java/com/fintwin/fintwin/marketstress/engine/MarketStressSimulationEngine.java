package com.fintwin.fintwin.marketstress.engine;

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
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.engine.MonthlyAdjustmentProvider;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MarketStressSimulationEngine {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private static final int RATE_SCALE = 16;
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final MonthlyFinancialSimulationEngine monthlyEngine;

    public MarketStressSimulationEngine(MonthlyFinancialSimulationEngine monthlyEngine) {
        this.monthlyEngine = monthlyEngine;
    }

    public MarketStressResult simulate(SimulationInput input, SimulationAssumptions assumptions,
                                       YearMonth startYearMonth, int horizonMonths,
                                       MarketExposure exposure, MarketStressScenario scenario,
                                       BigDecimal targetNetWorth) {
        validate(input, startYearMonth, horizonMonths, exposure, scenario);
        SimulationSummary baseline = monthlyEngine.simulate(input, assumptions, startYearMonth, horizonMonths);
        MarketImpactBreakdown preliminaryImpact = calculateInvestmentImpact(
                assumptions, startYearMonth, exposure, scenario);
        SimulationSummary stressed = monthlyEngine.simulate(input, assumptions, startYearMonth, horizonMonths,
                MonthlyAdjustmentProvider.none(), new MarketStressEffectProvider(scenario, preliminaryImpact));

        BigDecimal additionalDebtInterest = money(stressed.finalCumulativeTotals().debtInterest()
                .subtract(baseline.finalCumulativeTotals().debtInterest()));
        BigDecimal finalNetWorthDelta = money(last(stressed).netWorth().subtract(last(baseline).netWorth()));
        MarketImpactBreakdown impact = new MarketImpactBreakdown(scenario.shockYearMonth(),
                preliminaryImpact.domesticExposureAtShock(), preliminaryImpact.domesticStockImpact(),
                preliminaryImpact.overseasExposureAtShock(), preliminaryImpact.overseasStockImpact(),
                preliminaryImpact.exchangeRateImpact(), preliminaryImpact.totalInvestmentImpact(),
                additionalDebtInterest, finalNetWorthDelta);
        RiskComparison risk = compareRisk(baseline, stressed);
        GoalMarginComparison goal = compareGoal(baseline, stressed, targetNetWorth);
        return new MarketStressResult(baseline, stressed, impact, risk, goal,
                warnings(impact, risk, goal, scenario));
    }

    private void validate(SimulationInput input, YearMonth startYearMonth, int horizonMonths,
                          MarketExposure exposure, MarketStressScenario scenario) {
        YearMonth end = startYearMonth.plusMonths(horizonMonths - 1L);
        if (scenario.shockYearMonth().isBefore(startYearMonth) || scenario.shockYearMonth().isAfter(end)) {
            throw new IllegalArgumentException("shockYearMonth must be within the simulation period");
        }
        if (exposure.totalStockExposure().compareTo(input.initialInvestmentAssets()) > 0) {
            throw new IllegalArgumentException("stock exposure cannot exceed current investment assets");
        }
    }

    private MarketImpactBreakdown calculateInvestmentImpact(SimulationAssumptions assumptions,
                                                             YearMonth startYearMonth,
                                                             MarketExposure exposure,
                                                             MarketStressScenario scenario) {
        int shockMonthNumber = Math.toIntExact(startYearMonth.until(scenario.shockYearMonth(), ChronoUnit.MONTHS)) + 1;
        BigDecimal monthlyInvestmentRate = assumptions.annualInvestmentReturnRate()
                .divide(ONE_HUNDRED, RATE_SCALE, ROUNDING)
                .divide(TWELVE, RATE_SCALE, ROUNDING);
        BigDecimal domesticAtShock = growToShock(exposure.domesticStockAmount(), monthlyInvestmentRate,
                shockMonthNumber);
        BigDecimal overseasAtShock = growToShock(exposure.overseasStockAmount(), monthlyInvestmentRate,
                shockMonthNumber);
        BigDecimal domesticImpact = applyPercentage(domesticAtShock, scenario.domesticStockShockRate());
        BigDecimal overseasImpact = applyPercentage(overseasAtShock, scenario.overseasStockShockRate());
        BigDecimal overseasAfterMarketShock = money(overseasAtShock.add(overseasImpact));
        BigDecimal exchangeImpact = applyPercentage(overseasAfterMarketShock,
                scenario.krwUsdExchangeRateShockRate());
        BigDecimal totalImpact = money(domesticImpact.add(overseasImpact).add(exchangeImpact));
        return new MarketImpactBreakdown(scenario.shockYearMonth(), domesticAtShock, domesticImpact,
                overseasAtShock, overseasImpact, exchangeImpact, totalImpact,
                money(BigDecimal.ZERO), money(BigDecimal.ZERO));
    }

    private BigDecimal growToShock(BigDecimal initial, BigDecimal monthlyRate, int shockMonthNumber) {
        BigDecimal value = money(initial);
        for (int month = 1; month <= shockMonthNumber; month++) {
            value = money(value.add(value.multiply(monthlyRate)));
        }
        return value;
    }

    private BigDecimal applyPercentage(BigDecimal amount, BigDecimal percentage) {
        return money(amount.multiply(percentage).divide(ONE_HUNDRED, RATE_SCALE, ROUNDING));
    }

    private RiskComparison compareRisk(SimulationSummary baseline, SimulationSummary stressed) {
        RiskSnapshot baselineRisk = riskSnapshot(baseline);
        RiskSnapshot stressedRisk = riskSnapshot(stressed);
        return new RiskComparison(baselineRisk, stressedRisk,
                !baselineRisk.cashShortfall() && stressedRisk.cashShortfall(),
                !baselineRisk.negativeAmortization() && stressedRisk.negativeAmortization());
    }

    private RiskSnapshot riskSnapshot(SimulationSummary summary) {
        List<MonthlySimulationResult> results = summary.monthlyResults();
        List<MonthlySimulationResult> shortfalls = results.stream()
                .filter(MonthlySimulationResult::cashShortfall).toList();
        List<MonthlySimulationResult> negativeAmortization = results.stream()
                .filter(MonthlySimulationResult::negativeAmortization).toList();
        BigDecimal minimumLiquidity = results.stream().map(MonthlySimulationResult::liquidAssets)
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        return new RiskSnapshot(!shortfalls.isEmpty(), shortfalls.size(),
                shortfalls.isEmpty() ? null : shortfalls.getFirst().yearMonth(),
                !negativeAmortization.isEmpty(), negativeAmortization.size(),
                negativeAmortization.isEmpty() ? null : negativeAmortization.getFirst().yearMonth(),
                minimumLiquidity, last(summary).remainingDebt());
    }

    private GoalMarginComparison compareGoal(SimulationSummary baseline, SimulationSummary stressed,
                                               BigDecimal targetNetWorth) {
        BigDecimal baselineFinal = last(baseline).netWorth();
        BigDecimal stressedFinal = last(stressed).netWorth();
        if (targetNetWorth == null) {
            return GoalMarginComparison.notProvided(baselineFinal, stressedFinal);
        }
        BigDecimal baselineMargin = money(baselineFinal.subtract(targetNetWorth));
        BigDecimal stressedMargin = money(stressedFinal.subtract(targetNetWorth));
        boolean baselineAchievable = baselineMargin.signum() >= 0;
        boolean stressedAchievable = stressedMargin.signum() >= 0;
        GoalMarginStatus status;
        if (baselineAchievable && stressedAchievable) {
            status = GoalMarginStatus.BOTH_ACHIEVABLE;
        } else if (baselineAchievable) {
            status = GoalMarginStatus.BASELINE_ONLY;
        } else if (stressedAchievable) {
            status = GoalMarginStatus.STRESS_ONLY;
        } else {
            status = GoalMarginStatus.NEITHER_ACHIEVABLE;
        }
        return new GoalMarginComparison(status, money(targetNetWorth), baselineFinal, stressedFinal,
                baselineMargin, stressedMargin, money(stressedMargin.subtract(baselineMargin)));
    }

    private List<MarketStressWarning> warnings(MarketImpactBreakdown impact, RiskComparison risk,
                                                GoalMarginComparison goal, MarketStressScenario scenario) {
        List<MarketStressWarning> warnings = new ArrayList<>();
        if (impact.totalInvestmentImpact().signum() < 0) {
            warnings.add(new MarketStressWarning(MarketStressWarningCode.INVESTMENT_ASSET_LOSS,
                    "The configured market and exchange-rate shocks reduce investment assets."));
        }
        if (scenario.loanInterestRateChangePercentagePoints().signum() > 0
                && impact.additionalDebtInterest().signum() > 0) {
            warnings.add(new MarketStressWarning(MarketStressWarningCode.LOAN_INTEREST_INCREASE,
                    "The configured loan-rate change increases cumulative debt interest."));
        }
        if (risk.stressed().cashShortfall()) {
            warnings.add(new MarketStressWarning(MarketStressWarningCode.CASH_SHORTFALL,
                    "The stressed simulation contains at least one cash-shortfall month."));
        }
        if (risk.stressed().negativeAmortization()) {
            warnings.add(new MarketStressWarning(MarketStressWarningCode.NEGATIVE_AMORTIZATION,
                    "The stressed simulation contains at least one negative-amortization month."));
        }
        if (goal.marginDelta() != null && goal.marginDelta().signum() < 0) {
            warnings.add(new MarketStressWarning(MarketStressWarningCode.GOAL_MARGIN_REDUCED,
                    "The configured stress reduces the simulated goal margin."));
        }
        return List.copyOf(warnings);
    }

    private MonthlySimulationResult last(SimulationSummary summary) {
        return summary.monthlyResults().getLast();
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }
}
