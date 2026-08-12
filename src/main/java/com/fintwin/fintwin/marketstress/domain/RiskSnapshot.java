package com.fintwin.fintwin.marketstress.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record RiskSnapshot(
        boolean cashShortfall,
        int cashShortfallMonthCount,
        YearMonth firstCashShortfallMonth,
        boolean negativeAmortization,
        int negativeAmortizationMonthCount,
        YearMonth firstNegativeAmortizationMonth,
        BigDecimal minimumLiquidAssets,
        BigDecimal finalRemainingDebt
) {
}
