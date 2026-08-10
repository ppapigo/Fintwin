package com.fintwin.fintwin.goal.solver;

import com.fintwin.fintwin.goal.domain.FinancialGoal;
import com.fintwin.fintwin.goal.domain.GoalPlan;
import com.fintwin.fintwin.goal.domain.GoalPlanStatus;
import com.fintwin.fintwin.goal.domain.GoalPlanType;
import com.fintwin.fintwin.goal.domain.GoalSolverMetadata;
import com.fintwin.fintwin.goal.domain.GoalSolverResult;
import com.fintwin.fintwin.goal.domain.GoalStatus;
import com.fintwin.fintwin.goal.domain.GoalWarning;
import com.fintwin.fintwin.goal.domain.GoalWarningCode;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.scenario.engine.ScenarioMonthlyAdjustmentProvider;
import com.fintwin.fintwin.simulation.domain.MonthlySimulationResult;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.domain.SimulationSummary;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GoalReverseSolver {
    public static final int MAX_ITERATIONS_PER_PLAN = 128;
    public static final BigDecimal SEARCH_RESOLUTION = BigDecimal.ONE;
    public static final BigDecimal INCOME_SEARCH_UPPER_LIMIT = new BigDecimal("99999999999999999");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final String SEARCH_ALGORITHM =
            "Exponential upper-bound search followed by one-won binary search and boundary verification";
    private static final String MONOTONICITY_BASIS =
            "Larger supported actions create non-decreasing cash inflow or expense relief; investment transfers "
                    + "only relieved cash and the supported monthly investment factor remains positive.";

    private final MonthlyFinancialSimulationEngine simulationEngine;

    public GoalReverseSolver(MonthlyFinancialSimulationEngine simulationEngine) {
        this.simulationEngine = Objects.requireNonNull(simulationEngine);
    }

    public GoalSolverResult solve(SimulationInput input, SimulationAssumptions assumptions, FinancialGoal goal) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(assumptions);
        Objects.requireNonNull(goal);

        SimulationSummary baseline = simulate(input, assumptions, goal, List.of());
        BigDecimal currentNetWorth = currentNetWorth(input);
        BigDecimal baselineFinalNetWorth = baseline.monthlyResults().getLast().netWorth();
        BigDecimal goalGap = money(goal.targetAmount().subtract(baselineFinalNetWorth).max(BigDecimal.ZERO));
        YearMonth baselineFirstAchieved = firstAchievedMonth(currentNetWorth, baseline, goal);
        List<GoalWarning> baselineWarnings = simulationWarnings(baseline);

        if (baselineFinalNetWorth.compareTo(goal.targetAmount()) >= 0) {
            GoalWarning warning = warning(GoalWarningCode.ALREADY_ACHIEVABLE,
                    "The baseline already reaches the target; no additional monthly action is required.");
            List<GoalPlan> plans = List.of(
                    notRequiredPlan(GoalPlanType.REDUCE_EXPENSE, baseline, baselineFinalNetWorth,
                            currentNetWorth, goal, warning),
                    notRequiredPlan(GoalPlanType.INCREASE_INCOME, baseline, baselineFinalNetWorth,
                            currentNetWorth, goal, warning),
                    notRequiredPlan(GoalPlanType.REDUCE_EXPENSE_AND_INVEST, baseline, baselineFinalNetWorth,
                            currentNetWorth, goal, warning));
            List<GoalWarning> warnings = mergeWarnings(baselineWarnings, List.of(warning));
            return result(GoalStatus.ALREADY_ACHIEVABLE, currentNetWorth, baselineFinalNetWorth, goalGap,
                    baselineFirstAchieved, baseline, plans, warnings);
        }

        BigDecimal expenseReductionLimit = minimumBaselineVariableExpense(baseline)
                .min(INCOME_SEARCH_UPPER_LIMIT);
        GoalPlan expensePlan = solvePlan(GoalPlanType.REDUCE_EXPENSE, expenseReductionLimit,
                input, assumptions, goal, baseline);
        GoalPlan incomePlan = solvePlan(GoalPlanType.INCREASE_INCOME, INCOME_SEARCH_UPPER_LIMIT,
                input, assumptions, goal, baseline);
        GoalPlan investPlan = solvePlan(GoalPlanType.REDUCE_EXPENSE_AND_INVEST, expenseReductionLimit,
                input, assumptions, goal, baseline);
        List<GoalPlan> plans = List.of(expensePlan, incomePlan, investPlan);

        boolean expenseAchievable = expensePlan.achieved();
        boolean anyAchievable = plans.stream().anyMatch(GoalPlan::achieved);
        GoalStatus status = !anyAchievable ? GoalStatus.NOT_ACHIEVABLE
                : !expenseAchievable ? GoalStatus.PARTIALLY_ACHIEVABLE : GoalStatus.ACHIEVABLE;
        List<GoalWarning> warnings = new ArrayList<>(baselineWarnings);
        plans.forEach(plan -> warnings.addAll(plan.warnings()));
        return result(status, currentNetWorth, baselineFinalNetWorth, goalGap, baselineFirstAchieved,
                baseline, plans, mergeWarnings(warnings, List.of()));
    }

    private GoalSolverResult result(GoalStatus status, BigDecimal currentNetWorth,
                                    BigDecimal baselineFinalNetWorth, BigDecimal goalGap,
                                    YearMonth baselineFirstAchieved, SimulationSummary baseline,
                                    List<GoalPlan> plans, List<GoalWarning> warnings) {
        int totalIterations = plans.stream().mapToInt(GoalPlan::solverIterations).sum();
        GoalSolverMetadata metadata = new GoalSolverMetadata(SEARCH_RESOLUTION, MAX_ITERATIONS_PER_PLAN,
                INCOME_SEARCH_UPPER_LIMIT, totalIterations, SEARCH_ALGORITHM, MONOTONICITY_BASIS);
        return new GoalSolverResult(status, currentNetWorth, baselineFinalNetWorth, goalGap,
                baselineFirstAchieved, baseline, plans, metadata, warnings);
    }

    private GoalPlan solvePlan(GoalPlanType planType, BigDecimal rawLimit, SimulationInput input,
                               SimulationAssumptions assumptions, FinancialGoal goal,
                               SimulationSummary baseline) {
        BigDecimal limit = wholeWon(rawLimit.max(BigDecimal.ZERO));
        List<String> constraints = constraintsFor(planType, limit);
        if (limit.compareTo(SEARCH_RESOLUTION) < 0) {
            List<GoalWarning> warnings = new ArrayList<>();
            if (planType != GoalPlanType.INCREASE_INCOME) {
                warnings.add(warning(GoalWarningCode.EXPENSE_REDUCTION_INFEASIBLE,
                        "No whole-won variable-expense reduction is available throughout the goal period."));
            }
            warnings.addAll(planWarnings(planType, ZERO, input, assumptions, baseline));
            return infeasiblePlan(planType, ZERO, List.of(), baseline, input, goal, 0, constraints,
                    mergeWarnings(warnings, List.of()));
        }

        SearchOutcome search = findMinimum(planType, limit, input, assumptions, goal);
        if (!search.achieved()) {
            List<GoalWarning> warnings = new ArrayList<>();
            if (planType != GoalPlanType.INCREASE_INCOME) {
                warnings.add(warning(GoalWarningCode.EXPENSE_REDUCTION_INFEASIBLE,
                        "The target cannot be reached within the variable-expense reduction limit."));
            }
            warnings.add(warning(GoalWarningCode.SEARCH_LIMIT_REACHED,
                    "The target was not reached at the validated monthly action upper bound."));
            warnings.addAll(planWarnings(planType, search.maximumTested(), input, assumptions,
                    search.summary()));
            return infeasiblePlan(planType, search.maximumTested(),
                    eventsFor(planType, search.maximumTested(), goal), search.summary(), input, goal,
                    search.iterations(), constraints, mergeWarnings(warnings, List.of()));
        }

        BigDecimal requiredAmount = search.solution();
        List<FinancialEvent> events = eventsFor(planType, requiredAmount, goal);
        List<GoalWarning> warnings = planWarnings(planType, requiredAmount, input, assumptions, search.summary());
        BigDecimal finalNetWorth = search.summary().monthlyResults().getLast().netWorth();
        return new GoalPlan(planType, GoalPlanStatus.ACHIEVABLE, requiredAmount, search.maximumTested(), events,
                search.summary(), finalNetWorth, money(finalNetWorth.subtract(goal.targetAmount())),
                firstAchievedMonth(currentNetWorth(input), search.summary(), goal), true, search.iterations(),
                constraints, warnings);
    }

    private SearchOutcome findMinimum(GoalPlanType planType, BigDecimal limit, SimulationInput input,
                                      SimulationAssumptions assumptions, FinancialGoal goal) {
        int iterations = 0;
        BigDecimal lowerFailing = BigDecimal.ZERO;
        BigDecimal upper = SEARCH_RESOLUTION.min(limit);
        BigDecimal maximumTested = BigDecimal.ZERO;
        SimulationSummary upperSummary;

        while (true) {
            upperSummary = simulate(input, assumptions, goal, eventsFor(planType, upper, goal));
            iterations++;
            maximumTested = maximumTested.max(upper);
            if (works(upperSummary, goal)) {
                break;
            }
            lowerFailing = upper;
            if (upper.compareTo(limit) == 0 || iterations >= MAX_ITERATIONS_PER_PLAN - 2) {
                return new SearchOutcome(false, null, maximumTested, upperSummary, iterations);
            }
            upper = upper.multiply(TWO).min(limit);
        }

        while (upper.subtract(lowerFailing).compareTo(SEARCH_RESOLUTION) > 0
                && iterations < MAX_ITERATIONS_PER_PLAN - 2) {
            BigDecimal middle = lowerFailing.add(upper)
                    .divide(TWO, 0, RoundingMode.FLOOR);
            SimulationSummary middleSummary = simulate(input, assumptions, goal,
                    eventsFor(planType, middle, goal));
            iterations++;
            maximumTested = maximumTested.max(middle);
            if (works(middleSummary, goal)) {
                upper = middle;
                upperSummary = middleSummary;
            } else {
                lowerFailing = middle;
            }
        }

        SimulationSummary solutionSummary = simulate(input, assumptions, goal, eventsFor(planType, upper, goal));
        iterations++;
        maximumTested = maximumTested.max(upper);
        if (!works(solutionSummary, goal)) {
            return new SearchOutcome(false, null, maximumTested, solutionSummary, iterations);
        }
        if (upper.signum() > 0) {
            BigDecimal previous = upper.subtract(SEARCH_RESOLUTION);
            SimulationSummary previousSummary = simulate(input, assumptions, goal,
                    eventsFor(planType, previous, goal));
            iterations++;
            if (works(previousSummary, goal)) {
                throw new IllegalStateException("Goal solver minimum boundary verification failed");
            }
        }
        return new SearchOutcome(true, upper, maximumTested, solutionSummary, iterations);
    }

    private GoalPlan notRequiredPlan(GoalPlanType type, SimulationSummary baseline,
                                     BigDecimal finalNetWorth, BigDecimal currentNetWorth,
                                     FinancialGoal goal, GoalWarning warning) {
        return new GoalPlan(type, GoalPlanStatus.NOT_REQUIRED, ZERO, ZERO, List.of(), baseline,
                finalNetWorth, money(finalNetWorth.subtract(goal.targetAmount())),
                firstAchievedMonth(currentNetWorth, baseline, goal), true, 0,
                constraintsFor(type, ZERO), List.of(warning));
    }

    private GoalPlan infeasiblePlan(GoalPlanType type, BigDecimal maximumTested,
                                    List<FinancialEvent> events, SimulationSummary summary, SimulationInput input,
                                    FinancialGoal goal,
                                    int iterations, List<String> constraints, List<GoalWarning> warnings) {
        BigDecimal finalNetWorth = summary.monthlyResults().getLast().netWorth();
        return new GoalPlan(type, GoalPlanStatus.INFEASIBLE, null, maximumTested, events, summary,
                finalNetWorth, money(finalNetWorth.subtract(goal.targetAmount())),
                firstAchievedMonth(currentNetWorth(input), summary, goal), false, iterations, constraints, warnings);
    }

    private List<FinancialEvent> eventsFor(GoalPlanType planType, BigDecimal amount, FinancialGoal goal) {
        if (amount.signum() == 0) {
            return List.of();
        }
        YearMonth start = goal.startYearMonth();
        YearMonth end = goal.targetEndYearMonth();
        return switch (planType) {
            case REDUCE_EXPENSE -> List.of(new FinancialEvent.RecurringExpenseChange(
                    "goal-expense-reduction", "Goal reverse simulation expense reduction", start, end,
                    amount.negate()));
            case INCREASE_INCOME -> List.of(new FinancialEvent.IncomeChange(
                    "goal-income-increase", "Goal reverse simulation income increase", start, end, amount));
            case REDUCE_EXPENSE_AND_INVEST -> List.of(
                    new FinancialEvent.RecurringExpenseChange("goal-expense-reduction",
                            "Goal reverse simulation expense reduction", start, end, amount.negate()),
                    new FinancialEvent.InvestmentContributionChange("goal-investment-increase",
                            "Goal reverse simulation investment contribution increase", start, end, amount));
        };
    }

    private List<String> constraintsFor(GoalPlanType planType, BigDecimal limit) {
        List<String> constraints = new ArrayList<>();
        constraints.add("ONE_WON_SEARCH_RESOLUTION");
        constraints.add("MAXIMUM_" + MAX_ITERATIONS_PER_PLAN + "_ITERATIONS_PER_PLAN");
        if (planType == GoalPlanType.INCREASE_INCOME) {
            constraints.add("MONTHLY_INCOME_INCREASE_SEARCH_LIMIT=" + INCOME_SEARCH_UPPER_LIMIT.toPlainString());
        } else {
            constraints.add("MONTHLY_REDUCTION_NOT_ABOVE_MINIMUM_BASELINE_VARIABLE_EXPENSE="
                    + limit.toPlainString());
            constraints.add("TOTAL_EXPENSE_FLOOR_ZERO");
        }
        if (planType == GoalPlanType.REDUCE_EXPENSE_AND_INVEST) {
            constraints.add("INVESTMENT_INCREASE_EQUALS_EXPENSE_REDUCTION");
            constraints.add("ACTUAL_INVESTMENT_LIMITED_BY_AVAILABLE_CASH");
        }
        return constraints;
    }

    private List<GoalWarning> planWarnings(GoalPlanType type, BigDecimal amount, SimulationInput input,
                                           SimulationAssumptions assumptions, SimulationSummary summary) {
        List<GoalWarning> warnings = new ArrayList<>(simulationWarnings(summary));
        if (type == GoalPlanType.INCREASE_INCOME && amount.compareTo(input.monthlyIncome()) > 0) {
            warnings.add(warning(GoalWarningCode.INCOME_INCREASE_EXCEEDS_CURRENT_INCOME,
                    "The required monthly income increase is greater than the current monthly income."));
        }
        if (type == GoalPlanType.REDUCE_EXPENSE_AND_INVEST) {
            if (assumptions.annualInvestmentReturnRate().signum() < 0) {
                warnings.add(warning(GoalWarningCode.NEGATIVE_INVESTMENT_RETURN,
                        "The user-provided annual investment return assumption is negative."));
            }
            if (assumptions.annualInvestmentReturnRate()
                    .compareTo(assumptions.annualDepositInterestRate()) < 0) {
                warnings.add(warning(GoalWarningCode.INVESTMENT_RETURN_BELOW_DEPOSIT_RATE,
                        "The user-provided investment return assumption is below the deposit interest assumption."));
            }
            BigDecimal plannedInvestment = money(input.plannedMonthlyInvestment().add(amount));
            boolean limited = summary.monthlyResults().stream()
                    .anyMatch(month -> month.investmentContribution().compareTo(plannedInvestment) < 0);
            if (limited) {
                warnings.add(warning(GoalWarningCode.INVESTMENT_CONTRIBUTION_CASH_LIMITED,
                        "At least one planned investment contribution was limited by available cash."));
            }
        }
        return mergeWarnings(warnings, List.of());
    }

    private List<GoalWarning> simulationWarnings(SimulationSummary summary) {
        List<GoalWarning> warnings = new ArrayList<>();
        if (summary.monthlyResults().stream().anyMatch(MonthlySimulationResult::cashShortfall)) {
            warnings.add(warning(GoalWarningCode.CASH_SHORTFALL,
                    "At least one month has a cash shortfall under the applied assumptions."));
        }
        if (summary.monthlyResults().stream().anyMatch(MonthlySimulationResult::negativeAmortization)) {
            warnings.add(warning(GoalWarningCode.NEGATIVE_AMORTIZATION,
                    "At least one month has negative debt amortization under the applied assumptions."));
        }
        return warnings;
    }

    private BigDecimal minimumBaselineVariableExpense(SimulationSummary baseline) {
        return baseline.monthlyResults().stream()
                .map(MonthlySimulationResult::variableExpenses)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .setScale(0, RoundingMode.FLOOR);
    }

    private SimulationSummary simulate(SimulationInput input, SimulationAssumptions assumptions,
                                       FinancialGoal goal, List<FinancialEvent> events) {
        return simulationEngine.simulate(input, assumptions, goal.startYearMonth(), goal.horizonMonths(),
                new ScenarioMonthlyAdjustmentProvider(events));
    }

    private boolean works(SimulationSummary summary, FinancialGoal goal) {
        return summary.monthlyResults().getLast().netWorth().compareTo(goal.targetAmount()) >= 0;
    }

    private YearMonth firstAchievedMonth(BigDecimal currentNetWorth, SimulationSummary summary,
                                         FinancialGoal goal) {
        if (currentNetWorth != null && currentNetWorth.compareTo(goal.targetAmount()) >= 0) {
            return goal.startYearMonth();
        }
        return summary.monthlyResults().stream()
                .filter(month -> month.netWorth().compareTo(goal.targetAmount()) >= 0)
                .map(MonthlySimulationResult::yearMonth)
                .findFirst()
                .orElse(null);
    }

    private List<GoalWarning> mergeWarnings(List<GoalWarning> first, List<GoalWarning> second) {
        Map<GoalWarningCode, GoalWarning> warnings = new LinkedHashMap<>();
        first.forEach(warning -> warnings.putIfAbsent(warning.code(), warning));
        second.forEach(warning -> warnings.putIfAbsent(warning.code(), warning));
        return List.copyOf(warnings.values());
    }

    private GoalWarning warning(GoalWarningCode code, String message) {
        return new GoalWarning(code, message);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal currentNetWorth(SimulationInput input) {
        return money(input.initialLiquidAssets().add(input.initialInvestmentAssets()).subtract(input.initialDebt()));
    }

    private BigDecimal wholeWon(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR);
    }

    private record SearchOutcome(boolean achieved, BigDecimal solution, BigDecimal maximumTested,
                                 SimulationSummary summary, int iterations) {
    }
}
