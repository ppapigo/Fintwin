package com.fintwin.fintwin.marketstress.domain;

import java.math.BigDecimal;
import java.time.YearMonth;

public record MarketImpactBreakdown(
        YearMonth shockYearMonth,
        BigDecimal domesticExposureAtShock,
        BigDecimal domesticStockImpact,
        BigDecimal overseasExposureAtShock,
        BigDecimal overseasStockImpact,
        BigDecimal exchangeRateImpact,
        BigDecimal totalInvestmentImpact,
        BigDecimal additionalDebtInterest,
        BigDecimal finalNetWorthDelta
) {
}
