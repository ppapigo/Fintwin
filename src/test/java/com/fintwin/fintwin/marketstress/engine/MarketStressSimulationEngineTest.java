package com.fintwin.fintwin.marketstress.engine;

import com.fintwin.fintwin.marketstress.domain.GoalMarginStatus;
import com.fintwin.fintwin.marketstress.domain.MarketExposure;
import com.fintwin.fintwin.marketstress.domain.MarketStressResult;
import com.fintwin.fintwin.marketstress.domain.MarketStressScenario;
import com.fintwin.fintwin.marketstress.domain.MarketStressWarningCode;
import com.fintwin.fintwin.simulation.domain.SimulationAssumptions;
import com.fintwin.fintwin.simulation.domain.SimulationInput;
import com.fintwin.fintwin.simulation.engine.MonthlyFinancialSimulationEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketStressSimulationEngineTest {
    private final MarketStressSimulationEngine engine =
            new MarketStressSimulationEngine(new MonthlyFinancialSimulationEngine());

    @Test
    void appliesDomesticOverseasExchangeAndLoanRateStressDeterministically() {
        SimulationInput input = input("5000000", "10000000", "12000000", "6");
        SimulationAssumptions assumptions = assumptions("0", "300000");
        MarketExposure exposure = new MarketExposure(decimal("4000000"), decimal("3000000"));
        MarketStressScenario scenario = scenario("2026-08", "-20", "-30", "10", "3");

        MarketStressResult first = engine.simulate(input, assumptions, YearMonth.of(2026, 8), 12,
                exposure, scenario, decimal("10000000"));
        MarketStressResult second = engine.simulate(input, assumptions, YearMonth.of(2026, 8), 12,
                exposure, scenario, decimal("10000000"));

        assertThat(first).isEqualTo(second);
        assertThat(first.marketImpactBreakdown().domesticStockImpact()).isEqualByComparingTo("-800000.00");
        assertThat(first.marketImpactBreakdown().overseasStockImpact()).isEqualByComparingTo("-900000.00");
        assertThat(first.marketImpactBreakdown().exchangeRateImpact()).isEqualByComparingTo("210000.00");
        assertThat(first.marketImpactBreakdown().totalInvestmentImpact()).isEqualByComparingTo("-1490000.00");
        assertThat(first.marketImpactBreakdown().additionalDebtInterest()).isPositive();
        assertThat(first.goalMarginComparison().marginDelta())
                .isEqualByComparingTo(first.marketImpactBreakdown().finalNetWorthDelta());
        assertThat(first.warnings()).extracting(warning -> warning.code())
                .contains(MarketStressWarningCode.INVESTMENT_ASSET_LOSS,
                        MarketStressWarningCode.LOAN_INTEREST_INCREASE,
                        MarketStressWarningCode.GOAL_MARGIN_REDUCED);
    }

    @Test
    void separatesCurrentMarketContextFromStressCalculation() {
        SimulationInput input = input("1000000", "10000000", "0", "0");
        MarketStressResult result = engine.simulate(input, assumptions("0", "0"),
                YearMonth.of(2026, 8), 12,
                new MarketExposure(decimal("2000000"), decimal("3000000")),
                scenario("2027-01", "-10", "-20", "-5", "0"), null);

        assertThat(result.goalMarginComparison().status()).isEqualTo(GoalMarginStatus.NOT_PROVIDED);
        assertThat(result.marketImpactBreakdown().shockYearMonth()).isEqualTo(YearMonth.of(2027, 1));
        assertThat(result.baseline().monthlyResults()).hasSize(12);
        assertThat(result.stressed().monthlyResults()).hasSize(12);
    }

    @Test
    void appliesInvestmentReturnBeforeTheOneTimeShockWithoutASecondShock() {
        SimulationInput input = new SimulationInput(decimal("0"), decimal("1200"), decimal("0"), decimal("0"),
                decimal("0"), decimal("0"), decimal("0"), decimal("0"), decimal("0"));
        MarketStressResult result = engine.simulate(input, assumptions("12", "0"),
                YearMonth.of(2026, 1), 12,
                new MarketExposure(decimal("1200"), decimal("0")),
                scenario("2026-02", "-50", "0", "0", "0"), null);

        assertThat(result.marketImpactBreakdown().domesticExposureAtShock()).isEqualByComparingTo("1224.12");
        assertThat(result.marketImpactBreakdown().domesticStockImpact()).isEqualByComparingTo("-612.06");
        assertThat(result.stressed().monthlyResults().get(1).investmentAssets()).isEqualByComparingTo("612.06");
        assertThat(result.stressed().monthlyResults().get(2).investmentAssets()).isEqualByComparingTo("618.18");
    }

    @Test
    void rejectsExposureAboveTheCurrentInvestmentAssets() {
        assertThatThrownBy(() -> engine.simulate(input("0", "1000", "0", "0"), assumptions("0", "0"),
                YearMonth.of(2026, 1), 12,
                new MarketExposure(decimal("700"), decimal("301")),
                scenario("2026-02", "-10", "-10", "0", "0"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("stock exposure cannot exceed current investment assets");
    }

    @Test
    void rejectsShockOutsideTheSimulationPeriod() {
        assertThatThrownBy(() -> engine.simulate(input("0", "1000", "0", "0"), assumptions("0", "0"),
                YearMonth.of(2026, 1), 12,
                new MarketExposure(decimal("1000"), decimal("0")),
                scenario("2027-01", "-10", "0", "0", "0"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shockYearMonth must be within the simulation period");
    }

    private SimulationInput input(String liquid, String investments, String debt, String debtRate) {
        return new SimulationInput(decimal(liquid), decimal(investments), decimal(debt), decimal(debtRate),
                decimal("4000000"), decimal("1500000"), decimal("800000"), decimal("500000"),
                decimal("500000"));
    }

    private SimulationAssumptions assumptions(String investmentReturn, String debtPayment) {
        return new SimulationAssumptions(decimal("0"), decimal("0"), decimal("0"),
                decimal(investmentReturn), decimal(debtPayment));
    }

    private MarketStressScenario scenario(String month, String domestic, String overseas,
                                          String exchange, String loanRateChange) {
        return new MarketStressScenario(YearMonth.parse(month), decimal(domestic), decimal(overseas),
                decimal(exchange), decimal(loanRateChange));
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
