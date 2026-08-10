import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./apiClient";
import {
  buildScenarioComparisonPayload,
  normalizeNaturalLanguageResponse,
  normalizePrivacyPreview,
  normalizeScenarioComparison,
  previewScenarioPayload,
  runNaturalLanguageWhatIf,
} from "./whatIfApi";
import { createFinancialEvent } from "../simulation/financialEvents";

vi.mock("./apiClient", () => ({ apiRequest: vi.fn() }));

const VALUES = {
  startYearMonth: "2026-08", horizonMonths: 60,
  annualIncomeGrowthRate: "0", annualInflationRate: "0", annualDepositInterestRate: "0",
  annualInvestmentReturnRate: "0", monthlyDebtPayment: "300000.00",
};

const MONTH = `{
  "monthNumber": 1, "yearMonth": "2026-08", "income": 3000000.00,
  "fixedExpenses": 800000.00, "variableExpenses": 700000.00, "oneTimeExpense": 0.00,
  "debtInterest": 37500.00, "debtPayment": 300000.00, "extraDebtRepayment": 0.00,
  "principalRepaid": 262500.00, "savingsAllocation": 300000.00,
  "investmentContribution": 200000.00, "depositInterest": 0.00, "investmentReturn": 0.00,
  "disposableCashFlow": 1200000.00, "liquidAssets": 6000000.00,
  "investmentAssets": 1000000.00, "totalFinancialAssets": 7000000.00,
  "remainingDebt": 9737500.00, "netWorth": -2737500.00,
  "cashShortfall": false, "negativeAmortization": false, "cumulativeTotals": {}
}`;

const COMPARISON = `{
  "financialProfileId": 9007199254740993, "financialProfileVersion": 2,
  "scenarioName": "test", "startYearMonth": "2026-08", "horizonMonths": 60,
  "assumptions": {"annualIncomeGrowthRate":0,"annualInflationRate":0,"annualDepositInterestRate":0,"annualInvestmentReturnRate":0,"monthlyDebtPayment":300000.00},
  "normalizedEvents": [{"eventId":"event-1","eventType":"ONE_TIME_EXPENSE","effectiveYearMonth":"2026-09","amount":1000000.00,"description":"지출"}],
  "baseline": {"financialProfileId":1,"financialProfileVersion":2,"monthlyResults":[${MONTH}],"checkpoints":[],"finalCumulativeTotals":{},"calculationBasis":{}},
  "whatIf": {"financialProfileId":1,"financialProfileVersion":2,"monthlyResults":[${MONTH.replace("7000000.00", "6000000.00")}],"checkpoints":[],"finalCumulativeTotals":{},"calculationBasis":{}},
  "checkpointComparisons": [],
  "finalComparison": {"monthNumber":60,"yearMonth":"2031-07","liquidAssetsDelta":-1000000.00,"investmentAssetsDelta":0.00,"totalFinancialAssetsDelta":-1000000.00,"debtDelta":0.00,"netWorthDelta":-1000000.00},
  "impactSummary": {"incomeDelta":0.00,"consumptionDelta":1000000.00,"debtInterestDelta":0.00,"principalRepaidDelta":0.00,"investmentContributionDelta":0.00,"investmentReturnDelta":0.00,"liquidAssetsDelta":-1000000.00,"debtDelta":0.00,"netWorthDelta":-1000000.00,"residualDelta":0.00},
  "warnings": []
}`;

describe("whatIfApi", () => {
  beforeEach(() => vi.mocked(apiRequest).mockReset());

  it("normalizes compare results exactly and removes internal profile identifiers", () => {
    const result = normalizeScenarioComparison(COMPARISON);

    expect(result.financialProfileVersion).toBe(2);
    expect(result.normalizedEvents[0].amount).toBe("1000000.00");
    expect(result.finalComparison.netWorthDelta).toBe("-1000000.00");
    expect(result).not.toHaveProperty("financialProfileId");
    expect(result.baseline).not.toHaveProperty("financialProfileId");
  });

  it("builds compare requests without user, profile, provider, or unused event fields", () => {
    const event = { ...createFinancialEvent("INCOME_PAUSE", "event-safe"), description: "휴식", startYearMonth: "2027-01", endYearMonth: "2027-03" };
    const body = buildScenarioComparisonPayload("휴식", VALUES, [event]);
    const serialized = JSON.stringify(body);

    expect(body.events[0]).toEqual({ eventId: "event-safe", eventType: "INCOME_PAUSE", description: "휴식", startYearMonth: "2027-01", endYearMonth: "2027-03" });
    expect(serialized).not.toMatch(/userId|financialProfileId|provider|vault|tool/i);
  });

  it("shows only tokenized preview data and reference types", () => {
    const preview = normalizePrivacyPreview(JSON.stringify({
      status: "SAFE",
      externalPayload: { schemaVersion: "1", purpose: "SCENARIO_EVENT_EXTRACTION", locale: "ko-KR", currentYearMonth: "2026-08", sanitizedScenarioText: "[MONEY_1] 자동차", supportedEventTypes: ["ONE_TIME_EXPENSE"], supportedReferenceTypes: ["MONEY"], outputContractVersion: "1" },
      references: [{ referenceId: "MONEY_1", referenceType: "MONEY", value: "30000000" }],
      blockedIdentifierTypes: [], privacyNotice: "safe",
    }));

    expect(preview.externalPayload.sanitizedScenarioText).toBe("[MONEY_1] 자동차");
    expect(preview.referenceTypes).toEqual(["MONEY"]);
    expect(JSON.stringify(preview)).not.toContain("30000000");
    expect(JSON.stringify(preview)).not.toContain("referenceId");
  });

  it("normalizes completed agent metadata, risks, explanation, and safe trace fields", () => {
    const response = normalizeNaturalLanguageResponse(JSON.stringify({
      aiUsed: true, provider: "openai", model: "test-model", privacyMode: "STRICT",
      financialValuesTokenized: true, agentStatus: "COMPLETED", resultType: "SCENARIO_COMPARISON",
      typedResult: { startYearMonth: "2026-08", horizonMonths: 36, finalYearMonth: "2029-07", baselineFinalNetWorth: 100.00, whatIfFinalNetWorth: 80.00, netWorthDelta: -20.00, liquidAssetsDelta: -20.00, debtDelta: 0.00, cumulativeIncomeDelta: 0.00, cumulativeConsumptionDelta: 20.00 },
      risks: [{ code: "NET_WORTH_DECREASE", severity: "WARNING", evidenceField: "typedResult.netWorthDelta", summary: "감소" }],
      explanation: { headline: "비교", summary: "차이", evidence: [{ fieldPath: "typedResult.netWorthDelta", value: "-20.00" }], assumptionNotice: "가정", disclaimer: "면책" },
      trace: [{ sequence: 1, state: "RECEIVED", component: "orchestrator", outcomeCode: "accepted", rawValue: "secret" }],
      toolCallCount: 1,
    }));

    expect(response.status).toBe("COMPLETED");
    expect(response.typedResult.netWorthDelta).toBe("-20");
    expect(response.trace[0]).toEqual({ sequence: 1, state: "RECEIVED", component: "orchestrator", outcomeCode: "accepted" });
  });

  it("uses the preview endpoint once and sends only scenarioText", async () => {
    vi.mocked(apiRequest).mockResolvedValue('{"status":"BLOCKED","externalPayload":null,"references":[],"blockedIdentifierTypes":["EMAIL"],"privacyNotice":"blocked"}');

    await previewScenarioPayload("문장");

    expect(apiRequest).toHaveBeenCalledOnce();
    expect(apiRequest).toHaveBeenCalledWith("/api/privacy/scenario-payload-preview", { method: "POST", body: { scenarioText: "문장" }, responseType: "text" });
  });

  it("sends the natural-language contract without identity or provider fields", async () => {
    vi.mocked(apiRequest).mockResolvedValue('{"agentStatus":"NEEDS_INPUT","typedResult":null,"toolCallCount":0}');

    await runNaturalLanguageWhatIf("내년에 자동차를 사면?", VALUES);

    expect(apiRequest).toHaveBeenCalledOnce();
    const request = vi.mocked(apiRequest).mock.calls[0][1];
    expect(request.body).toEqual({
      scenarioText: "내년에 자동차를 사면?",
      startYearMonth: "2026-08",
      horizonMonths: 60,
      assumptions: {
        annualIncomeGrowthRate: "0",
        annualInflationRate: "0",
        annualDepositInterestRate: "0",
        annualInvestmentReturnRate: "0",
        monthlyDebtPayment: "300000.00",
      },
    });
    expect(JSON.stringify(request.body)).not.toMatch(/userId|profileId|provider|vault|referenceValue|tool/i);
  });
});
