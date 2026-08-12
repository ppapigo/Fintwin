package com.fintwin.fintwin.simulation.engine;

import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationEffects;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyFinancialSimulationEngineTest {
    private final MonthlyFinancialSimulationEngine engine = new MonthlyFinancialSimulationEngine();

    @Test
    void calculatesTwelveSimpleMonthsWithZeroRates() {
        SimulationInput input = input("1000", "0", "0", "0", "100", "40", "10", "10", "20");

        SimulationSummary summary = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 12);

        MonthlySimulationResult first = summary.monthlyResults().getFirst();
        MonthlySimulationResult last = summary.monthlyResults().getLast();
        assertThat(first.disposableCashFlow()).isEqualByComparingTo("50.00");
        assertThat(first.liquidAssets()).isEqualByComparingTo("1030.00");
        assertThat(first.investmentAssets()).isEqualByComparingTo("20.00");
        assertThat(last.liquidAssets()).isEqualByComparingTo("1360.00");
        assertThat(last.investmentAssets()).isEqualByComparingTo("240.00");
        assertThat(last.netWorth()).isEqualByComparingTo("1600.00");
        assertThat(summary.finalCumulativeTotals().income()).isEqualByComparingTo("1200.00");
        assertThat(summary.finalCumulativeTotals().consumption()).isEqualByComparingTo("600.00");
    }

    @Test
    void returnsSameResultsForSameInput() {
        SimulationInput input = input("1000", "200", "300", "6", "100", "40", "10", "10", "20");
        SimulationAssumptions assumptions = assumptions("3", "2", "2.5", "5", "30");

        SimulationSummary first = engine.simulate(input, assumptions, YearMonth.of(2026, 8), 12);
        SimulationSummary second = engine.simulate(input, assumptions, YearMonth.of(2026, 8), 12);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void appliesGrowthAndInflationStartingInMonthTwo() {
        SimulationInput input = input("0", "0", "0", "0", "1200", "100", "50", "0", "0");

        SimulationSummary summary = engine.simulate(input, assumptions("12", "12", "0", "0", "0"),
                YearMonth.of(2026, 8), 12);

        MonthlySimulationResult monthOne = summary.monthlyResults().get(0);
        MonthlySimulationResult monthTwo = summary.monthlyResults().get(1);
        assertThat(monthOne.income()).isEqualByComparingTo("1200.00");
        assertThat(monthOne.fixedExpenses()).isEqualByComparingTo("100.00");
        assertThat(monthOne.variableExpenses()).isEqualByComparingTo("50.00");
        assertThat(monthTwo.income()).isEqualByComparingTo("1212.00");
        assertThat(monthTwo.fixedExpenses()).isEqualByComparingTo("101.00");
        assertThat(monthTwo.variableExpenses()).isEqualByComparingTo("50.50");
    }

    @Test
    void calculatesDepositInterestFromOpeningBalance() {
        SimulationSummary summary = engine.simulate(
                input("1200", "0", "0", "0", "0", "0", "0", "0", "0"),
                assumptions("0", "0", "12", "0", "0"), YearMonth.of(2026, 8), 12);

        assertThat(summary.monthlyResults().getFirst().depositInterest()).isEqualByComparingTo("12.00");
        assertThat(summary.monthlyResults().getFirst().liquidAssets()).isEqualByComparingTo("1212.00");
    }

    @Test
    void roundsMoneyHalfUpToTwoDecimals() {
        SimulationSummary summary = engine.simulate(
                input("1", "0", "0", "0", "0", "0", "0", "0", "0"),
                assumptions("0", "0", "6", "0", "0"), YearMonth.of(2026, 8), 12);

        assertThat(summary.monthlyResults().getFirst().depositInterest()).isEqualByComparingTo("0.01");
    }

    @Test
    void calculatesPositiveAndNegativeInvestmentReturnsFromOpeningBalance() {
        SimulationInput input = input("0", "1200", "0", "0", "0", "0", "0", "0", "0");

        MonthlySimulationResult positive = engine.simulate(input, assumptions("0", "0", "0", "12", "0"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();
        MonthlySimulationResult negative = engine.simulate(input, assumptions("0", "0", "0", "-12", "0"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(positive.investmentReturn()).isEqualByComparingTo("12.00");
        assertThat(positive.investmentAssets()).isEqualByComparingTo("1212.00");
        assertThat(negative.investmentReturn()).isEqualByComparingTo("-12.00");
        assertThat(negative.investmentAssets()).isEqualByComparingTo("1188.00");
    }

    @Test
    void separatesDebtInterestAndPrincipal() {
        SimulationInput input = input("1000", "0", "1200", "12", "0", "0", "0", "0", "0");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "110"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.debtInterest()).isEqualByComparingTo("12.00");
        assertThat(result.debtPayment()).isEqualByComparingTo("110.00");
        assertThat(result.principalRepaid()).isEqualByComparingTo("98.00");
        assertThat(result.remainingDebt()).isEqualByComparingTo("1102.00");
        assertThat(result.netWorth()).isEqualByComparingTo("-212.00");
    }

    @Test
    void detectsNegativeAmortization() {
        SimulationInput input = input("1000", "0", "1200", "12", "0", "0", "0", "0", "0");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "10"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.debtInterest()).isEqualByComparingTo("12.00");
        assertThat(result.principalRepaid()).isEqualByComparingTo("0.00");
        assertThat(result.remainingDebt()).isEqualByComparingTo("1202.00");
        assertThat(result.negativeAmortization()).isTrue();
    }

    @Test
    void capsDebtPaymentAndStopsChargingAfterPayoff() {
        SimulationInput input = input("1000", "0", "100", "0", "0", "0", "0", "0", "0");

        SimulationSummary summary = engine.simulate(input, assumptions("0", "0", "0", "0", "500"),
                YearMonth.of(2026, 8), 12);

        MonthlySimulationResult monthOne = summary.monthlyResults().get(0);
        MonthlySimulationResult monthTwo = summary.monthlyResults().get(1);
        assertThat(monthOne.debtPayment()).isEqualByComparingTo("100.00");
        assertThat(monthOne.remainingDebt()).isEqualByComparingTo("0.00");
        assertThat(monthTwo.debtPayment()).isEqualByComparingTo("0.00");
        assertThat(monthTwo.liquidAssets()).isEqualByComparingTo("900.00");
    }

    @Test
    void investmentContributionDoesNotCreateNetWorth() {
        SimulationInput input = input("1000", "0", "0", "0", "0", "0", "0", "0", "100");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.liquidAssets()).isEqualByComparingTo("900.00");
        assertThat(result.investmentAssets()).isEqualByComparingTo("100.00");
        assertThat(result.netWorth()).isEqualByComparingTo("1000.00");
    }

    @Test
    void savingsAllocationDoesNotDoubleCountLiquidAssets() {
        SimulationInput input = input("1000", "0", "0", "0", "100", "0", "0", "80", "0");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.savingsAllocation()).isEqualByComparingTo("80.00");
        assertThat(result.liquidAssets()).isEqualByComparingTo("1100.00");
        assertThat(result.netWorth()).isEqualByComparingTo("1100.00");
    }

    @Test
    void principalPaymentDoesNotReduceNetWorthByItself() {
        SimulationInput input = input("1000", "0", "100", "0", "0", "0", "0", "0", "0");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "100"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.liquidAssets()).isEqualByComparingTo("900.00");
        assertThat(result.remainingDebt()).isEqualByComparingTo("0.00");
        assertThat(result.netWorth()).isEqualByComparingTo("900.00");
    }

    @Test
    void limitsInvestmentToAvailableCashAfterSavingsAllocation() {
        SimulationInput input = input("0", "0", "0", "0", "100", "90", "0", "0", "50");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.investmentContribution()).isEqualByComparingTo("10.00");
        assertThat(result.liquidAssets()).isEqualByComparingTo("0.00");
        assertThat(result.investmentAssets()).isEqualByComparingTo("10.00");
    }

    @Test
    void preservesNegativeCashAndSetsShortfallFlag() {
        SimulationInput input = input("0", "0", "0", "0", "50", "100", "0", "10", "20");

        MonthlySimulationResult result = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 12).monthlyResults().getFirst();

        assertThat(result.liquidAssets()).isEqualByComparingTo("-50.00");
        assertThat(result.savingsAllocation()).isEqualByComparingTo("0.00");
        assertThat(result.investmentContribution()).isEqualByComparingTo("0.00");
        assertThat(result.cashShortfall()).isTrue();
    }

    @Test
    void createsTwelveThirtySixAndSixtyMonthCheckpoints() {
        SimulationInput input = input("0", "0", "0", "0", "0", "0", "0", "0", "0");

        SimulationSummary twelve = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 12);
        SimulationSummary thirtySix = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 36);
        SimulationSummary sixty = engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 60);

        assertThat(twelve.checkpoints()).extracting(checkpoint -> checkpoint.monthNumber()).containsExactly(12);
        assertThat(thirtySix.checkpoints()).extracting(checkpoint -> checkpoint.monthNumber())
                .containsExactly(12, 36);
        assertThat(sixty.checkpoints()).extracting(checkpoint -> checkpoint.monthNumber())
                .containsExactly(12, 36, 60);
        assertThat(sixty.monthlyResults().getLast().yearMonth()).isEqualTo(YearMonth.of(2031, 7));
    }

    @Test
    void rejectsUnsupportedHorizon() {
        SimulationInput input = input("0", "0", "0", "0", "0", "0", "0", "0", "0");

        assertThatThrownBy(() -> engine.simulate(input, assumptions("0", "0", "0", "0", "0"),
                YearMonth.of(2026, 8), 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("horizonMonths must be one of 12, 36, or 60");
    }

    @Test
    void appliesExplicitMonthlyEffectsWithoutChangingExistingCallers() {
        SimulationInput input = input("1000", "1200", "1200", "12", "0", "0", "0", "0", "0");

        SimulationSummary stressed = engine.simulate(input, assumptions("0", "0", "0", "0", "20"),
                YearMonth.of(2026, 8), 12, MonthlyAdjustmentProvider.none(), yearMonth ->
                        yearMonth.equals(YearMonth.of(2026, 9))
                                ? new MonthlySimulationEffects(new BigDecimal("12"), new BigDecimal("-200"))
                                : MonthlySimulationEffects.none());

        assertThat(stressed.monthlyResults().getFirst().debtInterest()).isEqualByComparingTo("12.00");
        assertThat(stressed.monthlyResults().get(1).debtInterest()).isEqualByComparingTo("23.84");
        assertThat(stressed.monthlyResults().get(1).investmentAssets()).isEqualByComparingTo("1000.00");
        assertThat(stressed.monthlyResults().get(2).investmentAssets()).isEqualByComparingTo("1000.00");
    }

    private SimulationInput input(String liquidAssets, String investmentAssets, String debt,
                                  String annualDebtRate, String income, String fixedExpenses,
                                  String variableExpenses, String savings, String investment) {
        return new SimulationInput(decimal(liquidAssets), decimal(investmentAssets), decimal(debt),
                decimal(annualDebtRate), decimal(income), decimal(fixedExpenses), decimal(variableExpenses),
                decimal(savings), decimal(investment));
    }

    private SimulationAssumptions assumptions(String incomeGrowth, String inflation, String depositRate,
                                              String investmentReturn, String debtPayment) {
        return new SimulationAssumptions(decimal(incomeGrowth), decimal(inflation), decimal(depositRate),
                decimal(investmentReturn), decimal(debtPayment));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
