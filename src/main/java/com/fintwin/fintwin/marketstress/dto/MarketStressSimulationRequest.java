package com.fintwin.fintwin.marketstress.dto;

import com.fintwin.fintwin.marketstress.domain.MarketExposure;
import com.fintwin.fintwin.marketstress.domain.MarketStressScenario;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;

public record MarketStressSimulationRequest(
        @NotNull YearMonth startYearMonth,
        @NotNull Integer horizonMonths,
        @Valid @NotNull BaselineSimulationRequest.Assumptions assumptions,
        @Valid @NotNull Exposure exposure,
        @Valid @NotNull StressScenario stressScenario,
        @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal targetNetWorth
) {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);

    @AssertTrue(message = "horizonMonths must be one of 12, 36, or 60")
    public boolean isSupportedHorizon() {
        return horizonMonths == null || SUPPORTED_HORIZONS.contains(horizonMonths);
    }

    @AssertTrue(message = "shockYearMonth must be within the simulation period")
    public boolean isShockWithinSimulationPeriod() {
        if (startYearMonth == null || horizonMonths == null || stressScenario == null
                || stressScenario.shockYearMonth() == null || !SUPPORTED_HORIZONS.contains(horizonMonths)) {
            return true;
        }
        YearMonth end = startYearMonth.plusMonths(horizonMonths - 1L);
        return !stressScenario.shockYearMonth().isBefore(startYearMonth)
                && !stressScenario.shockYearMonth().isAfter(end);
    }

    public record Exposure(
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal domesticStockAmount,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal overseasStockAmount
    ) {
        public MarketExposure toDomain() {
            return new MarketExposure(domesticStockAmount, overseasStockAmount);
        }
    }

    public record StressScenario(
            @NotNull YearMonth shockYearMonth,
            @NotNull @DecimalMin("-100.000000") @DecimalMax("0.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal domesticStockShockRate,
            @NotNull @DecimalMin("-100.000000") @DecimalMax("0.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal overseasStockShockRate,
            @NotNull @DecimalMin("-100.000000") @DecimalMax("100.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal krwUsdExchangeRateShockRate,
            @NotNull @DecimalMin("-20.000000") @DecimalMax("20.000000")
            @Digits(integer = 2, fraction = 6) BigDecimal loanInterestRateChangePercentagePoints
    ) {
        public MarketStressScenario toDomain() {
            return new MarketStressScenario(shockYearMonth, domesticStockShockRate, overseasStockShockRate,
                    krwUsdExchangeRateShockRate, loanInterestRateChangePercentagePoints);
        }
    }
}
