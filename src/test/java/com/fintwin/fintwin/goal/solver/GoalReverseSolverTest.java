package com.fintwin.fintwin.goal.solver;

import com.fintwin.fintwin.goal.domain.FinancialGoal;
import com.fintwin.fintwin.goal.domain.GoalPlan;
import com.fintwin.fintwin.goal.domain.GoalPlanStatus;
import com.fintwin.fintwin.goal.domain.GoalPlanType;
import com.fintwin.fintwin.goal.domain.GoalSolverResult;
import com.fintwin.fintwin.goal.domain.GoalStatus;
import com.fintwin.fintwin.goal.domain.GoalType;
import com.fintwin.fintwin.goal.domain.GoalWarningCode;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.engine.ScenarioMonthlyAdjustmentProvider;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class GoalReverseSolverTest {
    private static final YearMonth START = YearMonth.of(2026, 8);
    private final MonthlyFinancialSimulationEngine engine = new MonthlyFinancialSimulationEngine();
    private final GoalReverseSolver solver = new GoalReverseSolver(engine);

    @Test
    void goldenFindsOneMillionMonthlyIncomeAndOneWonLessFails() {
        SimulationInput input = input("0", "0", "0", "0", "0", "0", "0", "0", "0");
        FinancialGoal goal = goal("12000000", 12);

        GoalSolverResult result = solver.solve(input, zeroAssumptions(), goal);
        GoalPlan plan = plan(result, GoalPlanType.INCREASE_INCOME);

        assertThat(plan.requiredMonthlyAmount()).isEqualByComparingTo("1000000");
        assertThat(plan.projectedFinalNetWorth()).isEqualByComparingTo("12000000.00");
        assertThat(plan.goalMargin()).isEqualByComparingTo("0.00");
        assertThat(finalNetWorth(input, zeroAssumptions(), goal, plan.planType(), "999999"))
                .isEqualByComparingTo("11999988.00");
    }

    @Test
    void goldenFindsFiveHundredThousandMonthlyExpenseReductionAndOneWonLessFails() {
        SimulationInput input = input("0", "0", "0", "0", "2000000", "0", "2000000", "0", "0");
        FinancialGoal goal = goal("6000000", 12);

        GoalSolverResult result = solver.solve(input, zeroAssumptions(), goal);
        GoalPlan plan = plan(result, GoalPlanType.REDUCE_EXPENSE);

        assertThat(plan.requiredMonthlyAmount()).isEqualByComparingTo("500000");
        assertThat(plan.goalMargin()).isEqualByComparingTo("0.00");
        assertThat(finalNetWorth(input, zeroAssumptions(), goal, plan.planType(), "499999"))
                .isEqualByComparingTo("5999988.00");
    }

    @Test
    void goldenAlreadyAchievableSkipsAllSearchIterations() {
        SimulationInput input = input("1000000", "0", "0", "0", "0", "0", "0", "0", "0");

        GoalSolverResult result = solver.solve(input, zeroAssumptions(), goal("1000000", 12));

        assertThat(result.goalStatus()).isEqualTo(GoalStatus.ALREADY_ACHIEVABLE);
        assertThat(result.solverMetadata().totalIterations()).isZero();
        assertThat(result.plans()).allSatisfy(plan -> {
            assertThat(plan.planStatus()).isEqualTo(GoalPlanStatus.NOT_REQUIRED);
            assertThat(plan.requiredMonthlyAmount()).isEqualByComparingTo("0.00");
            assertThat(plan.solverIterations()).isZero();
        });
        assertThat(result.warnings()).extracting(warning -> warning.code())
                .contains(GoalWarningCode.ALREADY_ACHIEVABLE);
    }

    @Test
    void goldenPositiveInvestmentReturnNeedsEightyFourInsteadOfOneHundredPerMonth() {
        SimulationInput input = input("0", "0", "0", "0", "1000", "0", "1000", "0", "0");
        SimulationAssumptions assumptions = assumptions("0", "0", "0", "12", "0");
        FinancialGoal goal = goal("3600", 36);

        GoalSolverResult result = solver.solve(input, assumptions, goal);
        GoalPlan cashPlan = plan(result, GoalPlanType.REDUCE_EXPENSE);
        GoalPlan investmentPlan = plan(result, GoalPlanType.REDUCE_EXPENSE_AND_INVEST);

        assertThat(cashPlan.requiredMonthlyAmount()).isEqualByComparingTo("100");
        assertThat(investmentPlan.requiredMonthlyAmount()).isEqualByComparingTo("84");
        assertThat(investmentPlan.requiredMonthlyAmount()).isLessThan(cashPlan.requiredMonthlyAmount());
        assertThat(investmentPlan.projectedFinalNetWorth()).isGreaterThanOrEqualTo(new BigDecimal("3600"));
        assertThat(finalNetWorth(input, assumptions, goal, investmentPlan.planType(), "83"))
                .isLessThan(new BigDecimal("3600"));
    }

    @Test
    void sameInputIsDeterministicAndPlansStayInSemanticOrder() {
        SimulationInput input = input("0", "0", "0", "0", "2000", "0", "1000", "0", "0");
        FinancialGoal goal = goal("20000", 12);

        GoalSolverResult first = solver.solve(input, zeroAssumptions(), goal);
        GoalSolverResult second = solver.solve(input, zeroAssumptions(), goal);

        assertThat(first).isEqualTo(second);
        assertThat(first.plans()).extracting(GoalPlan::planType).containsExactly(
                GoalPlanType.REDUCE_EXPENSE,
                GoalPlanType.INCREASE_INCOME,
                GoalPlanType.REDUCE_EXPENSE_AND_INVEST);
    }

    @Test
    void appliesVariableExpenseLimitAndReportsPartialAchievability() {
        SimulationInput input = input("0", "0", "0", "0", "0", "0", "100", "0", "0");

        GoalSolverResult result = solver.solve(input, zeroAssumptions(), goal("2400", 12));
        GoalPlan expensePlan = plan(result, GoalPlanType.REDUCE_EXPENSE);

        assertThat(result.goalStatus()).isEqualTo(GoalStatus.PARTIALLY_ACHIEVABLE);
        assertThat(expensePlan.planStatus()).isEqualTo(GoalPlanStatus.INFEASIBLE);
        assertThat(expensePlan.maximumMonthlyAmountTested()).isEqualByComparingTo("100");
        assertThat(expensePlan.warnings()).extracting(warning -> warning.code())
                .contains(GoalWarningCode.EXPENSE_REDUCTION_INFEASIBLE);
    }

    @Test
    void zeroInvestmentReturnMatchesCashPlanAndLowerReturnsProduceWarnings() {
        SimulationInput input = input("0", "0", "0", "0", "1000", "0", "1000", "0", "0");
        FinancialGoal goal = goal("12000", 12);

        GoalSolverResult zeroReturn = solver.solve(input, zeroAssumptions(), goal);
        assertThat(plan(zeroReturn, GoalPlanType.REDUCE_EXPENSE_AND_INVEST).requiredMonthlyAmount())
                .isEqualByComparingTo(plan(zeroReturn, GoalPlanType.REDUCE_EXPENSE).requiredMonthlyAmount());

        GoalSolverResult negativeReturn = solver.solve(input,
                assumptions("0", "0", "2", "-1", "0"), goal);
        assertThat(plan(negativeReturn, GoalPlanType.REDUCE_EXPENSE_AND_INVEST).warnings())
                .extracting(warning -> warning.code())
                .contains(GoalWarningCode.NEGATIVE_INVESTMENT_RETURN,
                        GoalWarningCode.INVESTMENT_RETURN_BELOW_DEPOSIT_RATE);
    }

    @Test
    void warnsWhenPlannedInvestmentIsLimitedByAvailableCash() {
        SimulationInput input = input("0", "0", "0", "0", "1000", "0", "1000", "1000", "0");

        GoalPlan plan = plan(solver.solve(input, zeroAssumptions(), goal("1200", 12)),
                GoalPlanType.REDUCE_EXPENSE_AND_INVEST);

        assertThat(plan.warnings()).extracting(warning -> warning.code())
                .contains(GoalWarningCode.INVESTMENT_CONTRIBUTION_CASH_LIMITED);
        assertThat(plan.projectedSummary().monthlyResults()).anySatisfy(month ->
                assertThat(month.investmentContribution()).isLessThan(plan.requiredMonthlyAmount()));
    }

    @Test
    void reportsCashDebtIncomeAndSearchLimitWarningsWithinIterationCap() {
        SimulationInput input = input("0", "0", "1200", "12", "0", "0", "100", "0", "0");
        FinancialGoal unreachableGoal = goal("1000000000000000000000000000000", 12);

        GoalSolverResult result = solver.solve(input, assumptions("0", "0", "0", "0", "10"),
                unreachableGoal);

        assertThat(result.goalStatus()).isEqualTo(GoalStatus.NOT_ACHIEVABLE);
        assertThat(result.warnings()).extracting(warning -> warning.code())
                .contains(GoalWarningCode.CASH_SHORTFALL, GoalWarningCode.NEGATIVE_AMORTIZATION,
                        GoalWarningCode.INCOME_INCREASE_EXCEEDS_CURRENT_INCOME,
                        GoalWarningCode.SEARCH_LIMIT_REACHED);
        assertThat(result.plans()).allSatisfy(plan ->
                assertThat(plan.solverIterations()).isLessThanOrEqualTo(GoalReverseSolver.MAX_ITERATIONS_PER_PLAN));
    }

    private BigDecimal finalNetWorth(SimulationInput input, SimulationAssumptions assumptions,
                                     FinancialGoal goal, GoalPlanType planType, String amount) {
        BigDecimal candidate = new BigDecimal(amount);
        java.util.List<FinancialEvent> events = switch (planType) {
            case INCREASE_INCOME -> java.util.List.of(new FinancialEvent.IncomeChange(
                    "verify", "verify", goal.startYearMonth(), goal.targetEndYearMonth(), candidate));
            case REDUCE_EXPENSE -> java.util.List.of(
                    new FinancialEvent.RecurringExpenseChange(
                            "verify", "verify", goal.startYearMonth(), goal.targetEndYearMonth(),
                            candidate.negate()));
            case REDUCE_EXPENSE_AND_INVEST -> java.util.List.of(
                    new FinancialEvent.RecurringExpenseChange("verify-expense", "verify",
                            goal.startYearMonth(), goal.targetEndYearMonth(), candidate.negate()),
                    new FinancialEvent.InvestmentContributionChange("verify-invest", "verify",
                            goal.startYearMonth(), goal.targetEndYearMonth(), candidate));
        };
        SimulationSummary summary = engine.simulate(input, assumptions, goal.startYearMonth(),
                goal.horizonMonths(), new ScenarioMonthlyAdjustmentProvider(events));
        return summary.monthlyResults().getLast().netWorth();
    }

    private GoalPlan plan(GoalSolverResult result, GoalPlanType type) {
        return result.plans().stream().filter(plan -> plan.planType() == type).findFirst().orElseThrow();
    }

    private FinancialGoal goal(String target, int horizon) {
        return new FinancialGoal(GoalType.TARGET_NET_WORTH, new BigDecimal(target), START, horizon);
    }

    private SimulationInput input(String liquid, String investments, String debt, String debtRate,
                                  String income, String fixedExpenses, String variableExpenses,
                                  String savings, String investment) {
        return new SimulationInput(decimal(liquid), decimal(investments), decimal(debt), decimal(debtRate),
                decimal(income), decimal(fixedExpenses), decimal(variableExpenses), decimal(savings),
                decimal(investment));
    }

    private SimulationAssumptions zeroAssumptions() {
        return assumptions("0", "0", "0", "0", "0");
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
