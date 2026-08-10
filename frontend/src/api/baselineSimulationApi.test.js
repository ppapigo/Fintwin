import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./apiClient";
import {
  buildBaselineSimulationPayload,
  normalizeBaselineSimulation,
  runBaselineSimulation,
  validateBaselineSimulation,
} from "./baselineSimulationApi";

vi.mock("./apiClient", () => ({ apiRequest: vi.fn() }));

const VALUES = {
  startYearMonth: "2026-08",
  horizonMonths: 60,
  annualIncomeGrowthRate: "3.0",
  annualInflationRate: "2.0",
  annualDepositInterestRate: "2.5",
  annualInvestmentReturnRate: "5.0",
  monthlyDebtPayment: "300000.00",
};

const RESPONSE = `{
  "financialProfileId": 9007199254740993,
  "financialProfileVersion": 3,
  "startYearMonth": "2026-08",
  "horizonMonths": 60,
  "assumptions": {
    "annualIncomeGrowthRate": 3.000000,
    "annualInflationRate": 2.000000,
    "annualDepositInterestRate": 2.500000,
    "annualInvestmentReturnRate": 5.000000,
    "monthlyDebtPayment": 300000.00
  },
  "monthlyResults": [{
    "monthNumber": 1,
    "yearMonth": "2026-08",
    "income": 99999999999999999.99,
    "fixedExpenses": 100.00,
    "variableExpenses": 200.00,
    "oneTimeExpense": 0.00,
    "debtInterest": 10.00,
    "debtPayment": 300000.00,
    "extraDebtRepayment": 0.00,
    "principalRepaid": 299990.00,
    "savingsAllocation": 300.00,
    "investmentContribution": 400.00,
    "depositInterest": 5.00,
    "investmentReturn": 15.00,
    "disposableCashFlow": 1000.00,
    "liquidAssets": 2000.00,
    "investmentAssets": 3000.00,
    "totalFinancialAssets": 5000.00,
    "remainingDebt": 7000.00,
    "netWorth": -2000.00,
    "cashShortfall": false,
    "negativeAmortization": false,
    "cumulativeTotals": {}
  }],
  "checkpoints": [],
  "finalCumulativeTotals": {},
  "calculationBasis": {}
}`;

describe("baselineSimulationApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("sends the nested backend contract without user or profile identifiers", async () => {
    vi.mocked(apiRequest).mockResolvedValue(RESPONSE);

    await runBaselineSimulation({ ...VALUES, userId: 1, financialProfileId: 2 });

    expect(apiRequest).toHaveBeenCalledOnce();
    expect(apiRequest).toHaveBeenCalledWith("/api/simulations/baseline", {
      method: "POST",
      body: buildBaselineSimulationPayload(VALUES),
      responseType: "text",
    });
    const body = vi.mocked(apiRequest).mock.calls[0][1].body;
    expect(JSON.stringify(body)).not.toMatch(/userId|financialProfileId/);
  });

  it("preserves BigDecimal text and removes the internal profile identifier", () => {
    const result = normalizeBaselineSimulation(RESPONSE);

    expect(result.financialProfileVersion).toBe(3);
    expect(result.monthlyResults[0].income).toBe("99999999999999999.99");
    expect(result.assumptions.monthlyDebtPayment).toBe("300000.00");
    expect(result).not.toHaveProperty("financialProfileId");
  });

  it("validates the exact backend ranges and supported horizons", () => {
    expect(validateBaselineSimulation(VALUES)).toEqual({});
    expect(validateBaselineSimulation({
      ...VALUES,
      horizonMonths: 24,
      annualDepositInterestRate: "-0.1",
      monthlyDebtPayment: "1.001",
    })).toMatchObject({
      horizonMonths: expect.any(String),
      annualDepositInterestRate: expect.any(String),
      monthlyDebtPayment: expect.any(String),
    });
  });
});
