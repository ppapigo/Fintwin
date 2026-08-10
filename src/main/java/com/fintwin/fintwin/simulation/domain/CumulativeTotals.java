package com.fintwin.fintwin.simulation.domain;

import java.math.BigDecimal;

public record CumulativeTotals(
        BigDecimal income,
        BigDecimal consumption,
        BigDecimal debtInterest,
        BigDecimal principalRepaid,
        BigDecimal savingsAllocated,
        BigDecimal investmentContributions,
        BigDecimal investmentReturn
) {
}
