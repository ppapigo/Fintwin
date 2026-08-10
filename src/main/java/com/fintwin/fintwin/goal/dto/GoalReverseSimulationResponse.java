package com.fintwin.fintwin.goal.dto;

import com.fintwin.fintwin.goal.domain.FinancialGoal;
import com.fintwin.fintwin.goal.domain.GoalPlan;
import com.fintwin.fintwin.goal.domain.GoalSolverMetadata;
import com.fintwin.fintwin.goal.domain.GoalSolverResult;
import com.fintwin.fintwin.goal.domain.GoalWarning;
import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationResponse;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record GoalReverseSimulationResponse(
        Long financialProfileId,
        int financialProfileVersion,
        String goalType,
        BigDecimal targetAmount,
        YearMonth startYearMonth,
        YearMonth targetEndYearMonth,
        int horizonMonths,
        BaselineSimulationResponse.AssumptionsResponse assumptions,
        String goalStatus,
        BigDecimal currentNetWorth,
        BigDecimal baselineFinalNetWorth,
        BigDecimal goalGap,
        YearMonth baselineFirstAchievedYearMonth,
        BaselineSimulationResponse baseline,
        List<GoalPlanResponse> plans,
        SolverMetadataResponse solverMetadata,
        List<GoalWarningResponse> warnings,
        String disclaimer
) {
    private static final String DISCLAIMER = "This is a deterministic simulation under user-provided assumptions, "
            + "not financial advice, a return forecast, or a guarantee that the goal will be achieved.";

    public static GoalReverseSimulationResponse from(Long profileId, int profileVersion, FinancialGoal goal,
                                                     SimulationAssumptions assumptions, GoalSolverResult result) {
        BaselineSimulationResponse baseline = BaselineSimulationResponse.from(profileId, profileVersion,
                goal.startYearMonth(), goal.horizonMonths(), assumptions, result.baselineSummary());
        return new GoalReverseSimulationResponse(profileId, profileVersion, goal.goalType().name(),
                goal.targetAmount(), goal.startYearMonth(), goal.targetEndYearMonth(), goal.horizonMonths(),
                baseline.assumptions(), result.goalStatus().name(), result.currentNetWorth(),
                result.baselineFinalNetWorth(), result.goalGap(), result.baselineFirstAchievedYearMonth(), baseline,
                result.plans().stream().map(plan -> GoalPlanResponse.from(profileId, profileVersion, goal,
                        assumptions, plan)).toList(), SolverMetadataResponse.from(result.solverMetadata()),
                result.warnings().stream().map(GoalWarningResponse::from).toList(), DISCLAIMER);
    }

    public record GoalPlanResponse(
            String planType,
            String planStatus,
            BigDecimal requiredMonthlyAmount,
            BigDecimal maximumMonthlyAmountTested,
            List<GoalEventResponse> generatedEvents,
            BigDecimal projectedFinalNetWorth,
            BigDecimal goalMargin,
            YearMonth firstAchievedYearMonth,
            boolean achieved,
            int solverIterations,
            List<String> appliedConstraints,
            List<GoalWarningResponse> warnings,
            BaselineSimulationResponse projectedResult
    ) {
        static GoalPlanResponse from(Long profileId, int profileVersion, FinancialGoal goal,
                                     SimulationAssumptions assumptions, GoalPlan plan) {
            BaselineSimulationResponse projection = BaselineSimulationResponse.from(profileId, profileVersion,
                    goal.startYearMonth(), goal.horizonMonths(), assumptions, plan.projectedSummary());
            return new GoalPlanResponse(plan.planType().name(), plan.planStatus().name(),
                    plan.requiredMonthlyAmount(), plan.maximumMonthlyAmountTested(),
                    plan.events().stream().map(GoalEventResponse::from).toList(), plan.projectedFinalNetWorth(),
                    plan.goalMargin(), plan.firstAchievedYearMonth(), plan.achieved(), plan.solverIterations(),
                    plan.appliedConstraints(), plan.warnings().stream().map(GoalWarningResponse::from).toList(),
                    projection);
        }
    }

    public record GoalEventResponse(
            String eventId,
            String eventType,
            YearMonth effectiveYearMonth,
            YearMonth startYearMonth,
            YearMonth endYearMonth,
            BigDecimal amount,
            BigDecimal monthlyDelta,
            String description
    ) {
        static GoalEventResponse from(FinancialEvent event) {
            return switch (event) {
                case FinancialEvent.OneTimeExpense oneTime -> new GoalEventResponse(oneTime.eventId(),
                        oneTime.eventType().name(), oneTime.effectiveYearMonth(), null, null, oneTime.amount(), null,
                        oneTime.description());
                case FinancialEvent.ExtraDebtRepayment repayment -> new GoalEventResponse(repayment.eventId(),
                        repayment.eventType().name(), repayment.effectiveYearMonth(), null, null,
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

        private static GoalEventResponse period(String id, String type, YearMonth start, YearMonth end,
                                                BigDecimal delta, String description) {
            return new GoalEventResponse(id, type, null, start, end, null, delta, description);
        }
    }

    public record SolverMetadataResponse(
            BigDecimal searchResolution,
            int maximumIterationsPerPlan,
            BigDecimal incomeSearchUpperLimit,
            int totalIterations,
            String searchAlgorithm,
            String monotonicityBasis
    ) {
        static SolverMetadataResponse from(GoalSolverMetadata metadata) {
            return new SolverMetadataResponse(metadata.searchResolution(), metadata.maximumIterationsPerPlan(),
                    metadata.incomeSearchUpperLimit(), metadata.totalIterations(), metadata.searchAlgorithm(),
                    metadata.monotonicityBasis());
        }
    }

    public record GoalWarningResponse(String code, String message) {
        static GoalWarningResponse from(GoalWarning warning) {
            return new GoalWarningResponse(warning.code().name(), warning.message());
        }
    }
}
