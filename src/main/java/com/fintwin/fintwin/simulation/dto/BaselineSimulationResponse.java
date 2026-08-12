package com.fintwin.fintwin.simulation.dto;

import com.fintwin.fintwin.simulation.domain.CumulativeTotals;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationCheckpoint;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record BaselineSimulationResponse(
        Long financialProfileId,
        int financialProfileVersion,
        YearMonth startYearMonth,
        int horizonMonths,
        AssumptionsResponse assumptions,
        List<MonthlyResultResponse> monthlyResults,
        List<CheckpointResponse> checkpoints,
        CumulativeTotalsResponse finalCumulativeTotals,
        CalculationBasisResponse calculationBasis
) {
    public static BaselineSimulationResponse from(Long profileId, int profileVersion, YearMonth startYearMonth,
                                                   int horizonMonths, SimulationAssumptions assumptions,
                                                   SimulationSummary summary) {
        return new BaselineSimulationResponse(profileId, profileVersion, startYearMonth, horizonMonths,
                AssumptionsResponse.from(assumptions),
                summary.monthlyResults().stream().map(MonthlyResultResponse::from).toList(),
                summary.checkpoints().stream().map(CheckpointResponse::from).toList(),
                CumulativeTotalsResponse.from(summary.finalCumulativeTotals()),
                CalculationBasisResponse.standard());
    }

    public record AssumptionsResponse(
            BigDecimal annualIncomeGrowthRate,
            BigDecimal annualInflationRate,
            BigDecimal annualDepositInterestRate,
            BigDecimal annualInvestmentReturnRate,
            BigDecimal monthlyDebtPayment
    ) {
        static AssumptionsResponse from(SimulationAssumptions assumptions) {
            return new AssumptionsResponse(assumptions.annualIncomeGrowthRate(), assumptions.annualInflationRate(),
                    assumptions.annualDepositInterestRate(), assumptions.annualInvestmentReturnRate(),
                    assumptions.monthlyDebtPayment());
        }
    }

    public record MonthlyResultResponse(
            int monthNumber,
            YearMonth yearMonth,
            BigDecimal income,
            BigDecimal fixedExpenses,
            BigDecimal variableExpenses,
            BigDecimal oneTimeExpense,
            BigDecimal debtInterest,
            BigDecimal debtPayment,
            BigDecimal extraDebtRepayment,
            BigDecimal principalRepaid,
            BigDecimal savingsAllocation,
            BigDecimal investmentContribution,
            BigDecimal depositInterest,
            BigDecimal investmentReturn,
            BigDecimal disposableCashFlow,
            BigDecimal liquidAssets,
            BigDecimal investmentAssets,
            BigDecimal totalFinancialAssets,
            BigDecimal remainingDebt,
            BigDecimal netWorth,
            boolean cashShortfall,
            boolean negativeAmortization,
            CumulativeTotalsResponse cumulativeTotals
    ) {
        public static MonthlyResultResponse from(MonthlySimulationResult result) {
            return new MonthlyResultResponse(result.monthNumber(), result.yearMonth(), result.income(),
                    result.fixedExpenses(), result.variableExpenses(), result.oneTimeExpense(), result.debtInterest(),
                    result.debtPayment(), result.extraDebtRepayment(), result.principalRepaid(),
                    result.savingsAllocation(), result.investmentContribution(),
                    result.depositInterest(), result.investmentReturn(), result.disposableCashFlow(),
                    result.liquidAssets(), result.investmentAssets(), result.totalFinancialAssets(),
                    result.remainingDebt(), result.netWorth(), result.cashShortfall(), result.negativeAmortization(),
                    CumulativeTotalsResponse.from(result.cumulativeTotals()));
        }
    }

    public record CheckpointResponse(
            int monthNumber,
            YearMonth yearMonth,
            BigDecimal liquidAssets,
            BigDecimal investmentAssets,
            BigDecimal totalFinancialAssets,
            BigDecimal remainingDebt,
            BigDecimal netWorth,
            CumulativeTotalsResponse cumulativeTotals
    ) {
        public static CheckpointResponse from(SimulationCheckpoint checkpoint) {
            return new CheckpointResponse(checkpoint.monthNumber(), checkpoint.yearMonth(), checkpoint.liquidAssets(),
                    checkpoint.investmentAssets(), checkpoint.totalFinancialAssets(), checkpoint.remainingDebt(),
                    checkpoint.netWorth(), CumulativeTotalsResponse.from(checkpoint.cumulativeTotals()));
        }
    }

    public record CumulativeTotalsResponse(
            BigDecimal income,
            BigDecimal consumption,
            BigDecimal debtInterest,
            BigDecimal principalRepaid,
            BigDecimal savingsAllocated,
            BigDecimal investmentContributions,
            BigDecimal investmentReturn
    ) {
        public static CumulativeTotalsResponse from(CumulativeTotals totals) {
            return new CumulativeTotalsResponse(totals.income(), totals.consumption(), totals.debtInterest(),
                    totals.principalRepaid(), totals.savingsAllocated(), totals.investmentContributions(),
                    totals.investmentReturn());
        }
    }

    public record CalculationBasisResponse(
            String monthlyRateFormula,
            String moneyRounding,
            String savingsTreatment,
            String investmentTreatment,
            String disclaimer
    ) {
        static CalculationBasisResponse standard() {
            return new CalculationBasisResponse("annual percentage / 100 / 12", "2 decimals, HALF_UP",
                    "Savings allocation remains within liquid assets and is not counted twice.",
                    "Investment contribution transfers liquid assets to investment assets.",
                    "This is a deterministic simulation based only on user-provided assumptions, not a forecast or guarantee.");
        }
    }
}
