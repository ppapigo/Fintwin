package com.fintwin.fintwin.simulation.engine;

import com.fintwin.fintwin.simulation.domain.CumulativeTotals;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.MonthlyAdjustments;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationEffects;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationCheckpoint;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MonthlyFinancialSimulationEngine {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);
    private static final BigDecimal ZERO = MoneyMath.money(BigDecimal.ZERO);

    public SimulationSummary simulate(SimulationInput input, SimulationAssumptions assumptions,
                                      YearMonth startYearMonth, int horizonMonths) {
        return simulate(input, assumptions, startYearMonth, horizonMonths, MonthlyAdjustmentProvider.none());
    }

    public SimulationSummary simulate(SimulationInput input, SimulationAssumptions assumptions,
                                      YearMonth startYearMonth, int horizonMonths,
                                      MonthlyAdjustmentProvider adjustmentProvider) {
        return simulate(input, assumptions, startYearMonth, horizonMonths, adjustmentProvider,
                MonthlySimulationEffectProvider.none());
    }

    public SimulationSummary simulate(SimulationInput input, SimulationAssumptions assumptions,
                                      YearMonth startYearMonth, int horizonMonths,
                                      MonthlyAdjustmentProvider adjustmentProvider,
                                      MonthlySimulationEffectProvider effectProvider) {
        if (!SUPPORTED_HORIZONS.contains(horizonMonths)) {
            throw new IllegalArgumentException("horizonMonths must be one of 12, 36, or 60");
        }

        BigDecimal incomeGrowthRate = MoneyMath.monthlyRate(assumptions.annualIncomeGrowthRate());
        BigDecimal inflationRate = MoneyMath.monthlyRate(assumptions.annualInflationRate());
        BigDecimal depositRate = MoneyMath.monthlyRate(assumptions.annualDepositInterestRate());
        BigDecimal investmentRate = MoneyMath.monthlyRate(assumptions.annualInvestmentReturnRate());

        BigDecimal liquidAssets = MoneyMath.money(input.initialLiquidAssets());
        BigDecimal investmentAssets = MoneyMath.money(input.initialInvestmentAssets());
        BigDecimal remainingDebt = MoneyMath.money(input.initialDebt());
        BigDecimal income = MoneyMath.money(input.monthlyIncome());
        BigDecimal fixedExpenses = MoneyMath.money(input.monthlyFixedExpenses());
        BigDecimal variableExpenses = MoneyMath.money(input.monthlyVariableExpenses());

        BigDecimal cumulativeIncome = ZERO;
        BigDecimal cumulativeConsumption = ZERO;
        BigDecimal cumulativeDebtInterest = ZERO;
        BigDecimal cumulativePrincipalRepaid = ZERO;
        BigDecimal cumulativeSavings = ZERO;
        BigDecimal cumulativeInvestmentContributions = ZERO;
        BigDecimal cumulativeInvestmentReturn = ZERO;

        List<MonthlySimulationResult> monthlyResults = new ArrayList<>(horizonMonths);
        List<SimulationCheckpoint> checkpoints = new ArrayList<>(3);

        for (int monthNumber = 1; monthNumber <= horizonMonths; monthNumber++) {
            if (monthNumber > 1) {
                income = grow(income, incomeGrowthRate);
                fixedExpenses = grow(fixedExpenses, inflationRate);
                variableExpenses = grow(variableExpenses, inflationRate);
            }

            BigDecimal openingLiquidAssets = liquidAssets;
            BigDecimal openingInvestmentAssets = investmentAssets;
            BigDecimal openingDebt = remainingDebt;
            YearMonth yearMonth = startYearMonth.plusMonths(monthNumber - 1L);
            MonthlyAdjustments adjustments = adjustmentProvider.adjustmentsFor(yearMonth);
            MonthlySimulationEffects effects = effectProvider.effectsFor(yearMonth);

            BigDecimal adjustedIncome = adjustments.incomePaused()
                    ? ZERO : MoneyMath.maxZero(income.add(adjustments.incomeDelta()));
            BigDecimal adjustedTotalExpenses = MoneyMath.maxZero(
                    fixedExpenses.add(variableExpenses).add(adjustments.expenseDelta()));
            BigDecimal adjustedFixedExpenses = MoneyMath.money(MoneyMath.min(fixedExpenses, adjustedTotalExpenses));
            BigDecimal adjustedVariableExpenses = MoneyMath.money(
                    adjustedTotalExpenses.subtract(adjustedFixedExpenses));
            BigDecimal oneTimeExpense = MoneyMath.money(adjustments.oneTimeExpense());
            BigDecimal plannedInvestment = MoneyMath.maxZero(
                    input.plannedMonthlyInvestment().add(adjustments.investmentContributionDelta()));

            BigDecimal requestedAnnualDebtRate = input.annualDebtInterestRate()
                    .add(effects.annualDebtInterestRateDelta());
            BigDecimal effectiveAnnualDebtRate = requestedAnnualDebtRate.signum() < 0
                    ? BigDecimal.ZERO : requestedAnnualDebtRate;
            BigDecimal debtRate = MoneyMath.monthlyRate(effectiveAnnualDebtRate);
            BigDecimal debtInterest = openingDebt.signum() > 0
                    ? MoneyMath.applyRate(openingDebt, debtRate) : ZERO;
            BigDecimal totalDebtDue = MoneyMath.money(openingDebt.add(debtInterest));
            BigDecimal baseDebtPayment = openingDebt.signum() > 0
                    ? MoneyMath.money(MoneyMath.min(MoneyMath.money(assumptions.monthlyDebtPayment()), totalDebtDue))
                    : ZERO;
            BigDecimal debtAfterBasePayment = MoneyMath.money(totalDebtDue.subtract(baseDebtPayment));
            BigDecimal cashAfterBaseObligations = MoneyMath.money(openingLiquidAssets
                    .add(adjustedIncome)
                    .subtract(adjustedFixedExpenses)
                    .subtract(adjustedVariableExpenses)
                    .subtract(oneTimeExpense)
                    .subtract(baseDebtPayment));
            BigDecimal extraDebtRepayment = MoneyMath.money(MoneyMath.min(
                    MoneyMath.min(MoneyMath.money(adjustments.extraDebtRepayment()), debtAfterBasePayment),
                    MoneyMath.maxZero(cashAfterBaseObligations)));
            BigDecimal debtPayment = MoneyMath.money(baseDebtPayment.add(extraDebtRepayment));
            BigDecimal principalRepaid = MoneyMath.maxZero(debtPayment.subtract(debtInterest));
            remainingDebt = MoneyMath.money(debtAfterBasePayment.subtract(extraDebtRepayment));
            boolean negativeAmortization = openingDebt.signum() > 0 && debtPayment.compareTo(debtInterest) < 0;

            BigDecimal disposableCashFlow = MoneyMath.money(adjustedIncome
                    .subtract(adjustedFixedExpenses)
                    .subtract(adjustedVariableExpenses)
                    .subtract(oneTimeExpense)
                    .subtract(debtPayment));
            BigDecimal cashAfterObligations = MoneyMath.money(openingLiquidAssets.add(disposableCashFlow));
            boolean cashShortfall = cashAfterObligations.signum() < 0;
            BigDecimal allocatableCash = MoneyMath.maxZero(cashAfterObligations);

            // Savings remain inside liquid assets; this records the reserved portion without moving cash.
            BigDecimal savingsAllocation = MoneyMath.money(MoneyMath.min(
                    MoneyMath.money(input.plannedMonthlySavings()), allocatableCash));
            BigDecimal availableForInvestment = MoneyMath.maxZero(allocatableCash.subtract(savingsAllocation));
            // Investment is an internal transfer from liquid assets and does not directly increase net worth.
            BigDecimal investmentContribution = MoneyMath.money(MoneyMath.min(
                    plannedInvestment, availableForInvestment));

            BigDecimal depositInterest = openingLiquidAssets.signum() > 0
                    ? MoneyMath.applyRate(openingLiquidAssets, depositRate) : ZERO;
            BigDecimal investmentReturn = MoneyMath.applyRate(openingInvestmentAssets, investmentRate);
            BigDecimal investmentValueAdjustment = MoneyMath.money(effects.investmentAssetValueAdjustment());

            liquidAssets = MoneyMath.money(cashAfterObligations
                    .subtract(investmentContribution)
                    .add(depositInterest));
            investmentAssets = MoneyMath.money(openingInvestmentAssets
                    .add(investmentContribution)
                    .add(investmentReturn)
                    .add(investmentValueAdjustment));
            if (investmentAssets.signum() < 0) {
                throw new IllegalArgumentException("investment asset adjustment cannot create a negative balance");
            }
            BigDecimal totalFinancialAssets = MoneyMath.money(liquidAssets.add(investmentAssets));
            BigDecimal netWorth = MoneyMath.money(totalFinancialAssets.subtract(remainingDebt));

            cumulativeIncome = MoneyMath.money(cumulativeIncome.add(adjustedIncome));
            cumulativeConsumption = MoneyMath.money(cumulativeConsumption.add(adjustedFixedExpenses)
                    .add(adjustedVariableExpenses).add(oneTimeExpense));
            cumulativeDebtInterest = MoneyMath.money(cumulativeDebtInterest.add(debtInterest));
            cumulativePrincipalRepaid = MoneyMath.money(cumulativePrincipalRepaid.add(principalRepaid));
            cumulativeSavings = MoneyMath.money(cumulativeSavings.add(savingsAllocation));
            cumulativeInvestmentContributions = MoneyMath.money(
                    cumulativeInvestmentContributions.add(investmentContribution));
            cumulativeInvestmentReturn = MoneyMath.money(cumulativeInvestmentReturn.add(investmentReturn));

            CumulativeTotals cumulativeTotals = new CumulativeTotals(cumulativeIncome, cumulativeConsumption,
                    cumulativeDebtInterest, cumulativePrincipalRepaid, cumulativeSavings,
                    cumulativeInvestmentContributions, cumulativeInvestmentReturn);
            MonthlySimulationResult result = new MonthlySimulationResult(monthNumber,
                    yearMonth, adjustedIncome, adjustedFixedExpenses, adjustedVariableExpenses, oneTimeExpense,
                    debtInterest, debtPayment, extraDebtRepayment, principalRepaid, savingsAllocation,
                    investmentContribution,
                    depositInterest, investmentReturn, disposableCashFlow, liquidAssets, investmentAssets,
                    totalFinancialAssets, remainingDebt, netWorth, cashShortfall, negativeAmortization,
                    cumulativeTotals);
            monthlyResults.add(result);
            if (monthNumber == 12 || monthNumber == 36 || monthNumber == 60) {
                checkpoints.add(SimulationCheckpoint.from(result));
            }
        }

        CumulativeTotals finalTotals = monthlyResults.getLast().cumulativeTotals();
        return new SimulationSummary(monthlyResults, checkpoints, finalTotals);
    }

    private BigDecimal grow(BigDecimal amount, BigDecimal monthlyRate) {
        return MoneyMath.money(amount.add(amount.multiply(monthlyRate)));
    }
}
