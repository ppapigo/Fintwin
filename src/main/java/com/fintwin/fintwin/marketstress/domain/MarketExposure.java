package com.fintwin.fintwin.marketstress.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record MarketExposure(BigDecimal domesticStockAmount, BigDecimal overseasStockAmount) {
    public MarketExposure {
        Objects.requireNonNull(domesticStockAmount);
        Objects.requireNonNull(overseasStockAmount);
        if (domesticStockAmount.signum() < 0 || overseasStockAmount.signum() < 0) {
            throw new IllegalArgumentException("market exposure amounts cannot be negative");
        }
    }

    public BigDecimal totalStockExposure() {
        return domesticStockAmount.add(overseasStockAmount);
    }
}
