import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./apiClient";
import {
  buildMultiScenarioPayload,
  compareMultipleScenarios,
  normalizeMultiScenarioComparison,
  validateScenarioLab,
} from "./scenarioLabApi";

vi.mock("./apiClient", () => ({ apiRequest: vi.fn() }));

const VALUES = {
  startYearMonth: "2026-08", horizonMonths: 60,
  annualIncomeGrowthRate: "0", annualInflationRate: "0",
  annualDepositInterestRate: "0", annualInvestmentReturnRate: "0",
  monthlyDebtPayment: "300000.00",
};
const EVENT = {
  eventId: "event-1", eventType: "ONE_TIME_EXPENSE", description: "합성 지출",
  effectiveYearMonth: "2026-09", startYearMonth: "ignored", endYearMonth: "ignored",
  amount: "99999999999999999.99", monthlyDelta: "ignored",
};
const SCENARIOS = [{ scenarioKey: "B", label: "자동차 구매", events: [EVENT] }];
const MONTH = `{
  "monthNumber":1,"yearMonth":"2026-08","income":3000000.00,"fixedExpenses":900000.00,
  "variableExpenses":500000.00,"oneTimeExpense":0.00,"debtInterest":10.00,"debtPayment":300000.00,
  "extraDebtRepayment":0.00,"principalRepaid":299990.00,"savingsAllocation":400000.00,
  "investmentContribution":200000.00,"depositInterest":0.00,"investmentReturn":0.00,
  "disposableCashFlow":1300000.00,"liquidAssets":6000000.00,"investmentAssets":8000000.00,
  "totalFinancialAssets":14000000.00,"remainingDebt":1700010.00,"netWorth":12299990.00,
  "cashShortfall":false,"negativeAmortization":false,"cumulativeTotals":{"income":3000000.00,"consumption":1400000.00,"debtInterest":10.00,"principalRepaid":299990.00,"savingsAllocated":400000.00,"investmentContributions":200000.00,"investmentReturn":0.00}
}`;
const RESPONSE = `{
  "financialProfileId":9007199254740993,"financialProfileVersion":3,"startYearMonth":"2026-08","horizonMonths":60,
  "assumptions":{"annualIncomeGrowthRate":0,"annualInflationRate":0,"annualDepositInterestRate":0,"annualInvestmentReturnRate":0,"monthlyDebtPayment":300000.00},
  "baseline":{"monthlyResults":[${MONTH}],"checkpoints":[],"finalCumulativeTotals":{},"finalLiquidAssets":6000000.00,"finalInvestmentAssets":8000000.00,"finalTotalFinancialAssets":14000000.00,"finalDebt":1700010.00,"finalNetWorth":12299990.00,"lastMonthDisposableCashFlow":1300000.00,"cashShortfall":false,"negativeAmortization":false},
  "scenarios":[{"scenarioKey":"B","label":"자동차 구매","normalizedEvents":[{"eventId":"event-1","eventType":"ONE_TIME_EXPENSE","effectiveYearMonth":"2026-09","amount":99999999999999999.99,"description":"합성 지출"}],"monthlyResults":[${MONTH}],"checkpoints":[],"finalCumulativeTotals":{},"finalLiquidAssets":5000000.00,"finalInvestmentAssets":8000000.00,"finalTotalFinancialAssets":13000000.00,"finalDebt":1700010.00,"finalNetWorth":11299990.00,"lastMonthDisposableCashFlow":300000.00,"baselineDelta":{"monthNumber":60,"yearMonth":"2031-07","netWorthDelta":-1000000.00},"residualDelta":0.00,"cashShortfall":false,"negativeAmortization":false,"warnings":[]}],
  "checkpointComparisons":[],"calculationWarnings":[{"scope":"SCENARIO","scenarioKey":"B","code":"NET_WORTH_BELOW_BASELINE","message":"raw backend text"}],
  "calculationBasis":{"monthlyRateFormula":"annual / 100 / 12","moneyRounding":"2 decimals","savingsTreatment":"liquid","investmentTreatment":"transfer"},"disclaimer":"deterministic only"
}`;

describe("scenarioLabApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("builds the exact multi contract with decimal strings and only active event fields", () => {
    const payload = buildMultiScenarioPayload({ ...VALUES, userId: 7, profileId: 99 }, SCENARIOS);
    expect(payload).toEqual({
      startYearMonth: "2026-08", horizonMonths: 60,
      assumptions: {
        annualIncomeGrowthRate: "0", annualInflationRate: "0", annualDepositInterestRate: "0",
        annualInvestmentReturnRate: "0", monthlyDebtPayment: "300000.00",
      },
      scenarios: [{ scenarioKey: "B", label: "자동차 구매", events: [{
        eventId: "event-1", eventType: "ONE_TIME_EXPENSE", description: "합성 지출",
        effectiveYearMonth: "2026-09", amount: "99999999999999999.99",
      }] }],
    });
    expect(JSON.stringify(payload)).not.toMatch(/userId|profileId|toolName|monthlyDelta|startYearMonth":"ignored/);
  });

  it("preserves backend decimal text and removes all internal profile identifiers and raw warning messages", () => {
    const result = normalizeMultiScenarioComparison(RESPONSE);
    expect(result.scenarios[0].normalizedEvents[0].amount).toBe("99999999999999999.99");
    expect(result.scenarios[0].baselineDelta.netWorthDelta).toBe("-1000000.00");
    expect(result.calculationWarnings[0]).toEqual({ scope: "SCENARIO", scenarioKey: "B", code: "NET_WORTH_BELOW_BASELINE", affectedYearMonth: null });
    expect(JSON.stringify(result)).not.toMatch(/financialProfileId|profileId|raw backend text/);
  });

  it("validates shared assumptions, scenario count, labels and events", () => {
    expect(validateScenarioLab(VALUES, SCENARIOS)).toEqual({ assumptionErrors: {}, scenarioErrors: {} });
    const invalid = validateScenarioLab({ ...VALUES, horizonMonths: 24 }, [
      ...SCENARIOS, { ...SCENARIOS[0], label: "bad\nlabel" }, ...SCENARIOS, ...SCENARIOS, ...SCENARIOS,
    ]);
    expect(invalid.assumptionErrors.horizonMonths).toBeTruthy();
    expect(invalid.scenarioErrors.scenarios).toBeTruthy();
    expect(Object.values(invalid.scenarioErrors)).toContain("Scenario 식별자가 중복됐습니다.");
  });

  it("uses the common API client once without retrying", async () => {
    vi.mocked(apiRequest).mockResolvedValue(RESPONSE);
    await compareMultipleScenarios(VALUES, SCENARIOS);
    expect(apiRequest).toHaveBeenCalledOnce();
    expect(apiRequest).toHaveBeenCalledWith("/api/simulations/compare-multiple", {
      method: "POST", body: buildMultiScenarioPayload(VALUES, SCENARIOS), responseType: "text",
    });
  });
});
