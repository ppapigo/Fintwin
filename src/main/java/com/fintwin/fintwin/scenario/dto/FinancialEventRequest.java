package com.fintwin.fintwin.scenario.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;

public record FinancialEventRequest(
        @NotBlank @Size(max = 100) String eventId,
        @NotBlank String eventType,
        YearMonth effectiveYearMonth,
        YearMonth startYearMonth,
        YearMonth endYearMonth,
        @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Digits(integer = 17, fraction = 2) BigDecimal monthlyDelta,
        @NotBlank @Size(max = 200) String description
) {
}
