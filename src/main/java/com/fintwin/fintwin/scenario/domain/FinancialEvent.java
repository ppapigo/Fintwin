package com.fintwin.fintwin.scenario.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public sealed interface FinancialEvent permits FinancialEvent.OneTimeExpense,
        FinancialEvent.RecurringExpenseChange, FinancialEvent.IncomeChange,
        FinancialEvent.IncomePause, FinancialEvent.InvestmentContributionChange,
        FinancialEvent.ExtraDebtRepayment {

    String eventId();

    String description();

    FinancialEventType eventType();

    record OneTimeExpense(String eventId, String description, YearMonth effectiveYearMonth,
                          BigDecimal amount) implements FinancialEvent {
        @Override
        public FinancialEventType eventType() {
            return FinancialEventType.ONE_TIME_EXPENSE;
        }
    }

    record RecurringExpenseChange(String eventId, String description, YearMonth startYearMonth,
                                  YearMonth endYearMonth, BigDecimal monthlyDelta) implements FinancialEvent {
        @Override
        public FinancialEventType eventType() {
            return FinancialEventType.RECURRING_EXPENSE_CHANGE;
        }
    }

    record IncomeChange(String eventId, String description, YearMonth startYearMonth,
                        YearMonth endYearMonth, BigDecimal monthlyDelta) implements FinancialEvent {
        @Override
        public FinancialEventType eventType() {
            return FinancialEventType.INCOME_CHANGE;
        }
    }

    record IncomePause(String eventId, String description, YearMonth startYearMonth,
                       YearMonth endYearMonth) implements FinancialEvent {
        @Override
        public FinancialEventType eventType() {
            return FinancialEventType.INCOME_PAUSE;
        }
    }

    record InvestmentContributionChange(String eventId, String description, YearMonth startYearMonth,
                                        YearMonth endYearMonth, BigDecimal monthlyDelta) implements FinancialEvent {
        @Override
        public FinancialEventType eventType() {
            return FinancialEventType.INVESTMENT_CONTRIBUTION_CHANGE;
        }
    }

    record ExtraDebtRepayment(String eventId, String description, YearMonth effectiveYearMonth,
                              BigDecimal amount) implements FinancialEvent {
        @Override
        public FinancialEventType eventType() {
            return FinancialEventType.EXTRA_DEBT_REPAYMENT;
        }
    }
}
