package com.fintwin.fintwin.scenario.engine;

import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioMonthlySimulationEngineTest {
    private static final YearMonth START = YearMonth.of(2026, 8);
    private final MonthlyFinancialSimulationEngine engine = new MonthlyFinancialSimulationEngine();
    private final SimulationAssumptions zeroRates = assumptions("0", "0", "0", "0", "0");

    @Test
    void noEventsProducesExactlyTheBaselineResult() {
        SimulationInput input = input("10000000", "2000000", "0", "0", "3000000", "1000000",
                "500000", "300000", "400000");

        SimulationSummary baseline = engine.simulate(input, zeroRates, START, 12);
        SimulationSummary scenario = engine.simulate(input, zeroRates, START, 12,
                new ScenarioMonthlyAdjustmentProvider(List.of()));

        assertThat(scenario).isEqualTo(baseline);
    }

    @Test
    void goldenOneTimeExpenseReducesNetWorthByExactlyOneMillionFromEffectiveMonth() {
        SimulationInput input = input("10000000", "0", "0", "0", "2000000", "500000", "500000",
                "0", "0");
        YearMonth effectiveMonth = START.plusMonths(3);
        var provider = provider(new FinancialEvent.OneTimeExpense("purchase", "purchase", effectiveMonth,
                decimal("1000000")));

        SimulationSummary baseline = engine.simulate(input, zeroRates, START, 12);
        SimulationSummary scenario = engine.simulate(input, zeroRates, START, 12, provider);

        for (int index = 0; index < 12; index++) {
            BigDecimal delta = scenario.monthlyResults().get(index).netWorth()
                    .subtract(baseline.monthlyResults().get(index).netWorth());
            assertThat(delta).isEqualByComparingTo(index < 3 ? "0.00" : "-1000000.00");
        }
    }

    @Test
    void goldenRecurringExpenseReductionAddsExactlySixHundredThousand() {
        SimulationInput input = input("0", "0", "0", "0", "2000000", "700000", "300000", "0", "0");
        var event = new FinancialEvent.RecurringExpenseChange("cost-cut", "cost cut", START,
                START.plusMonths(5), decimal("-100000"));

        SimulationSummary baseline = engine.simulate(input, zeroRates, START, 12);
        SimulationSummary scenario = engine.simulate(input, zeroRates, START, 12, provider(event));

        assertThat(scenario.monthlyResults().getLast().netWorth()
                .subtract(baseline.monthlyResults().getLast().netWorth())).isEqualByComparingTo("600000.00");
        assertThat(scenario.finalCumulativeTotals().consumption()
                .subtract(baseline.finalCumulativeTotals().consumption())).isEqualByComparingTo("-600000.00");
    }

    @Test
    void goldenIncomePauseReducesIncomeAndNetWorthByExactlySixMillion() {
        SimulationInput input = input("0", "0", "0", "0", "2000000", "0", "0", "0", "0");
        var pause = new FinancialEvent.IncomePause("leave", "leave", START.plusMonths(2), START.plusMonths(4));

        SimulationSummary baseline = engine.simulate(input, zeroRates, START, 12);
        SimulationSummary scenario = engine.simulate(input, zeroRates, START, 12, provider(pause));

        assertThat(scenario.finalCumulativeTotals().income()
                .subtract(baseline.finalCumulativeTotals().income())).isEqualByComparingTo("-6000000.00");
        assertThat(scenario.monthlyResults().getLast().netWorth()
                .subtract(baseline.monthlyResults().getLast().netWorth())).isEqualByComparingTo("-6000000.00");
    }

    @Test
    void eventOrderDoesNotChangeResultsAndSameTypeEventsAreSummed() {
        FinancialEvent incomeA = new FinancialEvent.IncomeChange("a", "a", START, START.plusMonths(2),
                decimal("100000"));
        FinancialEvent incomeB = new FinancialEvent.IncomeChange("b", "b", START, START.plusMonths(2),
                decimal("200000"));
        SimulationInput input = input("0", "0", "0", "0", "1000000", "0", "0", "0", "0");

        SimulationSummary first = engine.simulate(input, zeroRates, START, 12,
                new ScenarioMonthlyAdjustmentProvider(List.of(incomeA, incomeB)));
        SimulationSummary reversed = engine.simulate(input, zeroRates, START, 12,
                new ScenarioMonthlyAdjustmentProvider(List.of(incomeB, incomeA)));

        assertThat(first).isEqualTo(reversed);
        assertThat(first.monthlyResults().getFirst().income()).isEqualByComparingTo("1300000.00");
        assertThat(first.finalCumulativeTotals().income()).isEqualByComparingTo("12900000.00");
    }

    @Test
    void recurringExpenseChangesInTheSameMonthAreSummed() {
        SimulationInput input = input("0", "0", "0", "0", "1000000", "500000", "300000", "0", "0");
        List<FinancialEvent> events = List.of(
                new FinancialEvent.RecurringExpenseChange("a", "a", START, START, decimal("100000")),
                new FinancialEvent.RecurringExpenseChange("b", "b", START, START, decimal("-50000")));

        MonthlySimulationResult first = engine.simulate(input, zeroRates, START, 12,
                new ScenarioMonthlyAdjustmentProvider(events)).monthlyResults().getFirst();

        assertThat(first.fixedExpenses().add(first.variableExpenses())).isEqualByComparingTo("850000.00");
    }

    @Test
    void clampsIncomeExpensesAndInvestmentPlanAtZeroAndPeriodEndIsInclusive() {
        List<FinancialEvent> events = List.of(
                new FinancialEvent.IncomeChange("income", "income", START, START, decimal("-2000000")),
                new FinancialEvent.RecurringExpenseChange("expense", "expense", START, START,
                        decimal("-2000000")),
                new FinancialEvent.InvestmentContributionChange("investment", "investment", START, START,
                        decimal("-2000000")));
        SimulationInput input = input("0", "0", "0", "0", "1000000", "500000", "300000", "0",
                "400000");

        SimulationSummary result = engine.simulate(input, zeroRates, START, 12,
                new ScenarioMonthlyAdjustmentProvider(events));

        MonthlySimulationResult first = result.monthlyResults().getFirst();
        MonthlySimulationResult second = result.monthlyResults().get(1);
        assertThat(first.income()).isEqualByComparingTo("0.00");
        assertThat(first.fixedExpenses().add(first.variableExpenses())).isEqualByComparingTo("0.00");
        assertThat(first.investmentContribution()).isEqualByComparingTo("0.00");
        assertThat(second.income()).isEqualByComparingTo("1000000.00");
        assertThat(second.fixedExpenses().add(second.variableExpenses())).isEqualByComparingTo("800000.00");
    }

    @Test
    void incomePauseOverridesIncomeIncrease() {
        SimulationInput input = input("0", "0", "0", "0", "1000000", "0", "0", "0", "0");
        List<FinancialEvent> events = List.of(
                new FinancialEvent.IncomeChange("raise", "raise", START, START, decimal("500000")),
                new FinancialEvent.IncomePause("pause", "pause", START, START));

        MonthlySimulationResult first = engine.simulate(input, zeroRates, START, 12,
                new ScenarioMonthlyAdjustmentProvider(events)).monthlyResults().getFirst();

        assertThat(first.income()).isEqualByComparingTo("0.00");
    }

    @Test
    void extraRepaymentIsLimitedByDebtAndCashAndReducesFutureInterest() {
        SimulationInput input = input("500000", "0", "1000000", "12", "0", "0", "0", "0", "0");
        SimulationAssumptions debtAssumptions = assumptions("0", "0", "0", "0", "0");
        var event = new FinancialEvent.ExtraDebtRepayment("repay", "repay", START, decimal("2000000"));

        SimulationSummary baseline = engine.simulate(input, debtAssumptions, START, 12);
        SimulationSummary scenario = engine.simulate(input, debtAssumptions, START, 12, provider(event));

        assertThat(scenario.monthlyResults().getFirst().extraDebtRepayment()).isEqualByComparingTo("500000.00");
        assertThat(scenario.monthlyResults().getFirst().remainingDebt()).isEqualByComparingTo("510000.00");
        assertThat(scenario.monthlyResults().get(1).debtInterest())
                .isLessThan(baseline.monthlyResults().get(1).debtInterest());
    }

    @Test
    void extraRepaymentNeverExceedsRemainingDebt() {
        SimulationInput input = input("2000000", "0", "1000000", "0", "0", "0", "0", "0", "0");
        var event = new FinancialEvent.ExtraDebtRepayment("repay", "repay", START, decimal("2000000"));

        MonthlySimulationResult first = engine.simulate(input, zeroRates, START, 12, provider(event))
                .monthlyResults().getFirst();

        assertThat(first.extraDebtRepayment()).isEqualByComparingTo("1000000.00");
        assertThat(first.remainingDebt()).isEqualByComparingTo("0.00");
    }

    private ScenarioMonthlyAdjustmentProvider provider(FinancialEvent... events) {
        return new ScenarioMonthlyAdjustmentProvider(List.of(events));
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
