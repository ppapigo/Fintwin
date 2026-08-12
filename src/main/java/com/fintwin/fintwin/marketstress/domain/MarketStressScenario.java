package com.fintwin.fintwin.marketstress.domain;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

public record MarketStressScenario(
        YearMonth shockYearMonth,
        BigDecimal domesticStockShockRate,
        BigDecimal overseasStockShockRate,
        BigDecimal krwUsdExchangeRateShockRate,
        BigDecimal loanInterestRateChangePercentagePoints
) {
    private static final BigDecimal NEGATIVE_ONE_HUNDRED = new BigDecimal("-100");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal NEGATIVE_TWENTY = new BigDecimal("-20");
    private static final BigDecimal TWENTY = new BigDecimal("20");

    public MarketStressScenario {
        Objects.requireNonNull(shockYearMonth);
        Objects.requireNonNull(domesticStockShockRate);
        Objects.requireNonNull(overseasStockShockRate);
        Objects.requireNonNull(krwUsdExchangeRateShockRate);
        Objects.requireNonNull(loanInterestRateChangePercentagePoints);
        requireRange(domesticStockShockRate, NEGATIVE_ONE_HUNDRED, BigDecimal.ZERO,
                "domesticStockShockRate");
        requireRange(overseasStockShockRate, NEGATIVE_ONE_HUNDRED, BigDecimal.ZERO,
                "overseasStockShockRate");
        requireRange(krwUsdExchangeRateShockRate, NEGATIVE_ONE_HUNDRED, ONE_HUNDRED,
                "krwUsdExchangeRateShockRate");
        requireRange(loanInterestRateChangePercentagePoints, NEGATIVE_TWENTY, TWENTY,
                "loanInterestRateChangePercentagePoints");
    }

    private static void requireRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum, String field) {
        if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(field + " is outside the supported range");
        }
    }
}
