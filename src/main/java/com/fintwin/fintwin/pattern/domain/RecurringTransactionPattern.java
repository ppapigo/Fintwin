package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RecurringTransactionPattern(
        TransactionType type,
        TransactionCategory category,
        String displayDescription,
        int detectedMonthCount,
        int totalOccurrenceCount,
        BigDecimal averageOccurrencesPerMonth,
        BigDecimal averageMonthlyAmount,
        BigDecimal amountVariationRatePercent,
        YearMonth lastOccurrenceYearMonth,
        boolean fixedExpenseCandidate
) {
}
