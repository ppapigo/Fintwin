package com.fintwin.fintwin.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fintwin.fintwin.simulation.dto.BaselineSimulationRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Set;

public record NaturalLanguageWhatIfRequest(
        @NotBlank @Size(max = 500) String scenarioText,
        @NotNull YearMonth startYearMonth,
        @NotNull Integer horizonMonths,
        @Valid @NotNull Assumptions assumptions
) {
    private static final Set<Integer> SUPPORTED_HORIZONS = Set.of(12, 36, 60);

    @AssertTrue(message = "horizonMonths must be one of 12, 36, or 60")
    public boolean isSupportedHorizon() {
        return horizonMonths == null || SUPPORTED_HORIZONS.contains(horizonMonths);
    }

    public BaselineSimulationRequest.Assumptions toSimulationAssumptions() {
        return assumptions.toSimulationAssumptions();
    }

    public record Assumptions(
            @NotNull @DecimalMin("-100.000000") @DecimalMax("100.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal annualIncomeGrowthRate,
            @NotNull @DecimalMin("-100.000000") @DecimalMax("100.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal annualInflationRate,
            @NotNull @DecimalMin("0.000000") @DecimalMax("100.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal annualDepositInterestRate,
            @NotNull @DecimalMin("-100.000000") @DecimalMax("100.000000")
            @Digits(integer = 3, fraction = 6) BigDecimal annualInvestmentReturnRate,
            @JsonAlias("monthlyDebtRepayment")
            @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal monthlyDebtPayment
    ) {
        BaselineSimulationRequest.Assumptions toSimulationAssumptions() {
            return new BaselineSimulationRequest.Assumptions(annualIncomeGrowthRate, annualInflationRate,
                    annualDepositInterestRate, annualInvestmentReturnRate, monthlyDebtPayment);
        }
    }
}
