package com.fintwin.fintwin.pattern.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MonthlyCashFlowPattern(
        YearMonth yearMonth,
        int transactionCount,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal savingTransfers,
        BigDecimal investmentTransfers,
        BigDecimal debtPayments,
        BigDecimal transfers,
        BigDecimal monthlySurplus,
        BigDecimal liquidityAfterAllocations
) {
}
