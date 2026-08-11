import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./apiClient";
import {
  buildGoalReverseSimulationPayload,
  goalTargetEndYearMonth,
  normalizeGoalReverseSimulation,
  reverseSimulateGoal,
  validateGoalReverseSimulation,
} from "./goalReverseSimulationApi";

vi.mock("./apiClient", async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, apiRequest: vi.fn() };
});

const VALUES = {
  goalType: "TARGET_NET_WORTH",
  targetAmount: "50000000.00",
  startYearMonth: "2026-08",
  horizonMonths: 36,
  annualIncomeGrowthRate: "3.0",
  annualInflationRate: "2.0",
  annualDepositInterestRate: "2.5",
  annualInvestmentReturnRate: "5.0",
  monthlyDebtPayment: "300000.00",
};

const RESPONSE = `{
  "financialProfileId": 9007199254740993,
  "financialProfileVersion": 3,
  "goalType": "TARGET_NET_WORTH",
  "targetAmount": 99999999999999999.99,
  "startYearMonth": "2026-08",
  "targetEndYearMonth": "2029-07",
  "horizonMonths": 36,
  "assumptions": {"annualIncomeGrowthRate":3.0,"annualInflationRate":2.0,"annualDepositInterestRate":2.5,"annualInvestmentReturnRate":5.0,"monthlyDebtPayment":300000.00},
  "goalStatus": "ACHIEVABLE",
  "currentNetWorth": 12000000.00,
  "baselineFinalNetWorth": 32000000.00,
  "goalGap": 67999999999999999.99,
  "baselineFirstAchievedYearMonth": null,
  "baseline": {"financialProfileId":9007199254740993,"financialProfileVersion":3,"startYearMonth":"2026-08","horizonMonths":36,"assumptions":{},"monthlyResults":[{"monthNumber":1,"yearMonth":"2026-08","liquidAssets":2000000.00,"investmentAssets":3000000.00,"remainingDebt":1000000.00,"netWorth":4000000.00}],"checkpoints":[],"finalCumulativeTotals":{},"calculationBasis":{}},
  "plans": [{"planType":"REDUCE_EXPENSE","planStatus":"ACHIEVABLE","requiredMonthlyAmount":500000.00,"maximumMonthlyAmountTested":524288.00,"generatedEvents":[{"eventId":"internal-event","eventType":"RECURRING_EXPENSE_CHANGE","startYearMonth":"2026-08","endYearMonth":"2029-07","monthlyDelta":-500000.00,"description":"지출 절감"}],"projectedFinalNetWorth":99999999999999999.99,"goalMargin":0.00,"firstAchievedYearMonth":"2029-07","achieved":true,"solverIterations":41,"appliedConstraints":["VARIABLE_EXPENSE_LIMIT"],"warnings":[],"projectedResult":{"financialProfileId":9007199254740993,"financialProfileVersion":3,"startYearMonth":"2026-08","horizonMonths":36,"assumptions":{},"monthlyResults":[{"monthNumber":1,"yearMonth":"2026-08","liquidAssets":2500000.00,"investmentAssets":3000000.00,"remainingDebt":1000000.00,"netWorth":4500000.00,"cashShortfall":false,"negativeAmortization":false}],"checkpoints":[],"finalCumulativeTotals":{},"calculationBasis":{}}}],
  "solverMetadata":{"searchResolution":1.00,"maximumIterationsPerPlan":128,"incomeSearchUpperLimit":99999999999999999.99,"totalIterations":41,"searchAlgorithm":"BOUNDED_BINARY_SEARCH","monotonicityBasis":"FINAL_NET_WORTH"},
  "warnings":[{"code":"SEARCH_LIMIT_REACHED","message":"internal raw message"}],
  "disclaimer":"Deterministic simulation only."
}`;

describe("goalReverseSimulationApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("sends the exact nested DTO with decimal strings and no user or profile identifiers", async () => {
    vi.mocked(apiRequest).mockResolvedValue(RESPONSE);

    await reverseSimulateGoal({ ...VALUES, userId: 1, profileId: 2, financialProfileId: 3 });

    expect(apiRequest).toHaveBeenCalledOnce();
    const [, options] = vi.mocked(apiRequest).mock.calls[0];
    expect(options).toEqual({
      method: "POST",
      body: buildGoalReverseSimulationPayload(VALUES),
      responseType: "text",
    });
    expect(options.body.targetAmount).toBe("50000000.00");
    expect(options.body.assumptions.monthlyDebtPayment).toBe("300000.00");
    expect(JSON.stringify(options.body)).not.toMatch(/userId|profileId|financialProfileId/);
  });

  it("preserves every financial decimal as text and removes all internal identifiers", () => {
    const result = normalizeGoalReverseSimulation(RESPONSE);

    expect(result.targetAmount).toBe("99999999999999999.99");
    expect(result.goalGap).toBe("67999999999999999.99");
    expect(result.plans[0].requiredMonthlyAmount).toBe("500000.00");
    expect(result.plans[0].generatedEvents[0].monthlyDelta).toBe("-500000.00");
    expect(result.plans[0].projectedResult.monthlyResults[0].netWorth).toBe("4500000.00");
    expect(result).not.toHaveProperty("financialProfileId");
    expect(result.baseline).not.toHaveProperty("financialProfileId");
    expect(result.plans[0].projectedResult).not.toHaveProperty("financialProfileId");
    expect(result.plans[0].generatedEvents[0]).not.toHaveProperty("eventId");
    expect(result.warnings).toEqual([{ code: "SEARCH_LIMIT_REACHED" }]);
    expect(JSON.stringify(result)).not.toContain("internal raw message");
  });

  it("validates target money, supported period, target date, rate format, and debt payment", () => {
    expect(validateGoalReverseSimulation(VALUES, { totalLoanBalance: "10000000" })).toEqual({});
    const errors = validateGoalReverseSimulation({
      ...VALUES,
      targetAmount: "1e9",
      horizonMonths: 72,
      annualInvestmentReturnRate: "Infinity",
      monthlyDebtPayment: "",
    }, { totalLoanBalance: "10000000" });

    expect(errors).toMatchObject({
      targetAmount: expect.any(String),
      horizonMonths: expect.any(String),
      targetEndYearMonth: expect.any(String),
      annualInvestmentReturnRate: expect.any(String),
      monthlyDebtPayment: expect.any(String),
    });
    expect(validateGoalReverseSimulation({ ...VALUES, targetAmount: "1.001" }, {}))
      .toHaveProperty("targetAmount");
    expect(goalTargetEndYearMonth("2026-08", 36)).toBe("2029-07");
  });
});
