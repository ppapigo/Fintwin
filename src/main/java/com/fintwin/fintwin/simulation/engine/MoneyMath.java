package com.fintwin.fintwin.simulation.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class MoneyMath {
    static final int MONEY_SCALE = 2;
    static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final int RATE_SCALE = 16;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWELVE = new BigDecimal("12");

    private MoneyMath() {
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    static BigDecimal monthlyRate(BigDecimal annualPercentage) {
        return annualPercentage
                .divide(ONE_HUNDRED, RATE_SCALE, ROUNDING_MODE)
                .divide(TWELVE, RATE_SCALE, ROUNDING_MODE);
    }

    static BigDecimal applyRate(BigDecimal amount, BigDecimal rate) {
        return money(amount.multiply(rate));
    }

    static BigDecimal min(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    static BigDecimal maxZero(BigDecimal value) {
        return value.signum() < 0 ? money(BigDecimal.ZERO) : money(value);
    }
}
