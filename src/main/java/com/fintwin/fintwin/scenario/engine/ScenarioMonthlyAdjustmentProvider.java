package com.fintwin.fintwin.scenario.engine;

import com.fintwin.fintwin.scenario.domain.FinancialEvent;
import com.fintwin.fintwin.simulation.domain.MonthlyAdjustments;
import com.fintwin.fintwin.simulation.engine.MonthlyAdjustmentProvider;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public final class ScenarioMonthlyAdjustmentProvider implements MonthlyAdjustmentProvider {
    private final List<FinancialEvent> events;

    public ScenarioMonthlyAdjustmentProvider(List<FinancialEvent> events) {
        this.events = List.copyOf(events);
    }

    @Override
    public MonthlyAdjustments adjustmentsFor(YearMonth yearMonth) {
        BigDecimal incomeDelta = BigDecimal.ZERO;
        BigDecimal expenseDelta = BigDecimal.ZERO;
        BigDecimal investmentDelta = BigDecimal.ZERO;
        BigDecimal oneTimeExpense = BigDecimal.ZERO;
        BigDecimal extraDebtRepayment = BigDecimal.ZERO;
        boolean incomePaused = false;

        for (FinancialEvent event : events) {
            switch (event) {
                case FinancialEvent.OneTimeExpense oneTime when oneTime.effectiveYearMonth().equals(yearMonth) ->
                        oneTimeExpense = oneTimeExpense.add(oneTime.amount());
                case FinancialEvent.RecurringExpenseChange recurring
                        when includes(recurring.startYearMonth(), recurring.endYearMonth(), yearMonth) ->
                        expenseDelta = expenseDelta.add(recurring.monthlyDelta());
                case FinancialEvent.IncomeChange income
                        when includes(income.startYearMonth(), income.endYearMonth(), yearMonth) ->
                        incomeDelta = incomeDelta.add(income.monthlyDelta());
                case FinancialEvent.IncomePause pause
                        when includes(pause.startYearMonth(), pause.endYearMonth(), yearMonth) -> incomePaused = true;
                case FinancialEvent.InvestmentContributionChange investment
                        when includes(investment.startYearMonth(), investment.endYearMonth(), yearMonth) ->
                        investmentDelta = investmentDelta.add(investment.monthlyDelta());
                case FinancialEvent.ExtraDebtRepayment repayment
                        when repayment.effectiveYearMonth().equals(yearMonth) ->
                        extraDebtRepayment = extraDebtRepayment.add(repayment.amount());
                default -> {
                }
            }
        }
        return new MonthlyAdjustments(incomeDelta, expenseDelta, investmentDelta, oneTimeExpense,
                extraDebtRepayment, incomePaused);
    }

    private boolean includes(YearMonth start, YearMonth end, YearMonth target) {
        return !target.isBefore(start) && !target.isAfter(end);
    }
}
