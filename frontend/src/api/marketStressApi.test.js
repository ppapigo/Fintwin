import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./apiClient";
import {
  buildMarketStressPayload,
  getMarketContext,
  normalizeMarketStress,
  runMarketStress,
  validateMarketStress,
} from "./marketStressApi";

vi.mock("./apiClient", () => ({ apiRequest: vi.fn() }));

const VALUES = {
  startYearMonth: "2026-08",
  horizonMonths: 12,
  annualIncomeGrowthRate: "0",
  annualInflationRate: "0",
  annualDepositInterestRate: "0",
  annualInvestmentReturnRate: "0",
  monthlyDebtPayment: "300000.00",
  domesticStockAmount: "3000000.00",
  overseasStockAmount: "2000000.00",
  shockYearMonth: "2026-10",
  domesticStockShockRate: "-20.000000",
  overseasStockShockRate: "-25.000000",
  krwUsdExchangeRateShockRate: "10.000000",
  loanInterestRateChangePercentagePoints: "2.000000",
  targetNetWorth: "20000000.00",
};

const RESPONSE = `{
  "financialProfileId": 9007199254740993,
  "userId": 7,
  "financialProfileVersion": 4,
  "startYearMonth": "2026-08",
  "horizonMonths": 12,
  "assumptions": {"annualIncomeGrowthRate":0,"annualInflationRate":0,"annualDepositInterestRate":0,"annualInvestmentReturnRate":0,"monthlyDebtPayment":300000.00},
  "exposure": {"domesticStockAmount":3000000.00,"overseasStockAmount":2000000.00,"otherInvestmentAssets":5000000.00},
  "stressScenario": {"shockYearMonth":"2026-10","domesticStockShockRate":-20.000000,"overseasStockShockRate":-25.000000,"krwUsdExchangeRateShockRate":10.000000,"loanInterestRateChangePercentagePoints":2.000000},
  "marketContextUsage": {"usedInCalculation":false,"boundary":"separate"},
  "baseline": {"monthlyResults":[{"monthNumber":1,"yearMonth":"2026-08","netWorth":99999999999999999.99,"investmentAssets":10000000.00,"liquidAssets":7000000.00,"remainingDebt":12000000.00}],"checkpoints":[],"finalCumulativeTotals":{}},
  "stressed": {"monthlyResults":[{"monthNumber":1,"yearMonth":"2026-08","netWorth":4000000.00,"investmentAssets":9000000.00,"liquidAssets":7000000.00,"remainingDebt":12000000.00}],"checkpoints":[],"finalCumulativeTotals":{}},
  "marketImpactBreakdown": {"shockYearMonth":"2026-10","domesticExposureAtShock":3000000.00,"domesticStockImpact":-600000.00,"overseasExposureAtShock":2000000.00,"overseasStockImpact":-500000.00,"exchangeRateImpact":150000.00,"totalInvestmentImpact":-950000.00,"additionalDebtInterest":10000.00,"finalNetWorthDelta":-960000.00},
  "riskComparison": {"baseline":{"cashShortfall":false,"cashShortfallMonthCount":0,"negativeAmortization":false,"negativeAmortizationMonthCount":0,"minimumLiquidAssets":100.00,"finalRemainingDebt":1000.00},"stressed":{"cashShortfall":true,"cashShortfallMonthCount":1,"negativeAmortization":false,"negativeAmortizationMonthCount":0,"minimumLiquidAssets":-100.00,"finalRemainingDebt":1100.00},"newCashShortfall":true,"newNegativeAmortization":false},
  "goalMarginComparison": {"status":"BASELINE_ONLY","targetNetWorth":20000000.00,"baselineFinalNetWorth":21000000.00,"stressedFinalNetWorth":19000000.00,"baselineMargin":1000000.00,"stressedMargin":-1000000.00,"marginDelta":-2000000.00},
  "warnings":[{"code":"CASH_SHORTFALL","message":"internal provider text"}],
  "calculationBasis":{"marketShockOrder":"order"},"disclaimer":"safe"
}`;

describe("marketStressApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("sends string money and rates without user or profile identifiers", async () => {
    vi.mocked(apiRequest).mockResolvedValue(RESPONSE);

    await runMarketStress({ ...VALUES, userId: "7", profileId: "99" });

    const options = vi.mocked(apiRequest).mock.calls[0][1];
    expect(apiRequest).toHaveBeenCalledWith("/api/market-stress/simulate", expect.objectContaining({
      method: "POST", responseType: "text",
    }));
    expect(options.body).toEqual(buildMarketStressPayload(VALUES));
    expect(options.body.exposure.domesticStockAmount).toBe("3000000.00");
    expect(options.body.stressScenario.domesticStockShockRate).toBe("-20.000000");
    expect(JSON.stringify(options.body)).not.toMatch(/userId|profileId|financialProfileId/);
  });

  it("preserves exact BigDecimal text and removes internal identifiers and raw warning messages", () => {
    const result = normalizeMarketStress(RESPONSE);

    expect(result.baseline.monthlyResults[0].netWorth).toBe("99999999999999999.99");
    expect(result.marketImpactBreakdown.totalInvestmentImpact).toBe("-950000.00");
    expect(result.goalMarginComparison.stressedMargin).toBe("-1000000.00");
    expect(result).not.toHaveProperty("financialProfileId");
    expect(result).not.toHaveProperty("userId");
    expect(result.warnings).toEqual([{ code: "CASH_SHORTFALL" }]);
  });

  it("validates dates, ranges, exposure sum, and optional goal without Number money arithmetic", () => {
    expect(validateMarketStress(VALUES, { investmentAssets: "5000000.00" })).toEqual({});
    expect(validateMarketStress({
      ...VALUES,
      shockYearMonth: "2027-08",
      domesticStockAmount: "4000000.00",
      overseasStockAmount: "2000000.00",
      domesticStockShockRate: "1",
      targetNetWorth: "0",
    }, { investmentAssets: "5000000.00" })).toMatchObject({
      shockYearMonth: expect.any(String), exposure: expect.any(String),
      domesticStockShockRate: expect.any(String), targetNetWorth: expect.any(String),
    });
  });

  it("normalizes unavailable official context without fabricating a value", async () => {
    vi.mocked(apiRequest).mockResolvedValue(`{"status":"UNAVAILABLE","checkedAt":"2026-08-11T00:00:00Z","observations":[{"indicator":"KOSPI_INDEX","value":null,"status":"UNAVAILABLE","issueCode":"CREDENTIAL_MISSING","source":"KRX_OPEN_API"}]}`);

    const context = await getMarketContext();

    expect(apiRequest).toHaveBeenCalledWith("/api/market-stress/context", { responseType: "text" });
    expect(context.observations[0].value).toBeNull();
    expect(context.observations[0].status).toBe("UNAVAILABLE");
  });
});
