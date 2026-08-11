import { apiRequest } from "./apiClient";
import { normalizeBaselineSimulationPayload } from "./baselineSimulationApi";
import { buildFinancialEventPayload, validateFinancialEvents } from "../simulation/financialEvents";
import { buildSimulationRequestFields, validateSimulationAssumptions } from "../simulation/simulationAssumptions";

const DECIMAL_FIELDS = [
  "annualIncomeGrowthRate", "annualInflationRate", "annualDepositInterestRate",
  "annualInvestmentReturnRate", "monthlyDebtPayment", "amount", "monthlyDelta",
  "income", "fixedExpenses", "variableExpenses", "oneTimeExpense", "debtInterest",
  "debtPayment", "extraDebtRepayment", "principalRepaid", "savingsAllocation",
  "investmentContribution", "depositInterest", "investmentReturn", "disposableCashFlow",
  "liquidAssets", "investmentAssets", "totalFinancialAssets", "remainingDebt", "netWorth",
  "consumption", "savingsAllocated", "investmentContributions", "finalLiquidAssets",
  "finalInvestmentAssets", "finalTotalFinancialAssets", "finalDebt", "finalNetWorth",
  "lastMonthDisposableCashFlow", "liquidAssetsDelta", "investmentAssetsDelta",
  "totalFinancialAssetsDelta", "debtDelta", "netWorthDelta", "cumulativeIncomeDelta",
  "cumulativeConsumptionDelta", "cumulativeDebtInterestDelta", "cumulativePrincipalRepaidDelta",
  "cumulativeInvestmentContributionDelta", "cumulativeInvestmentReturnDelta", "residualDelta",
];
const DECIMAL_PATTERN = new RegExp(`(\\"(?:${DECIMAL_FIELDS.join("|")})\\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`, "g");

function parsePrecisionJson(text) {
  return JSON.parse(text.replace(DECIMAL_PATTERN, '$1"$2"'));
}

function decimal(value) {
  return value == null ? "0" : String(value);
}

function normalizeDelta(value) {
  if (!value || typeof value !== "object") return null;
  return {
    monthNumber: Number(value.monthNumber) || 0,
    yearMonth: String(value.yearMonth ?? ""),
    liquidAssetsDelta: decimal(value.liquidAssetsDelta),
    investmentAssetsDelta: decimal(value.investmentAssetsDelta),
    totalFinancialAssetsDelta: decimal(value.totalFinancialAssetsDelta),
    debtDelta: decimal(value.debtDelta),
    netWorthDelta: decimal(value.netWorthDelta),
    cumulativeIncomeDelta: decimal(value.cumulativeIncomeDelta),
    cumulativeConsumptionDelta: decimal(value.cumulativeConsumptionDelta),
    cumulativeDebtInterestDelta: decimal(value.cumulativeDebtInterestDelta),
    cumulativePrincipalRepaidDelta: decimal(value.cumulativePrincipalRepaidDelta),
    cumulativeInvestmentContributionDelta: decimal(value.cumulativeInvestmentContributionDelta),
    cumulativeInvestmentReturnDelta: decimal(value.cumulativeInvestmentReturnDelta),
  };
}

function normalizeEvent(event) {
  return {
    eventId: String(event?.eventId ?? ""),
    eventType: String(event?.eventType ?? ""),
    effectiveYearMonth: typeof event?.effectiveYearMonth === "string" ? event.effectiveYearMonth : null,
    startYearMonth: typeof event?.startYearMonth === "string" ? event.startYearMonth : null,
    endYearMonth: typeof event?.endYearMonth === "string" ? event.endYearMonth : null,
    amount: event?.amount == null ? null : String(event.amount),
    monthlyDelta: event?.monthlyDelta == null ? null : String(event.monthlyDelta),
    description: String(event?.description ?? ""),
  };
}

function normalizeWarning(warning) {
  return {
    scope: String(warning?.scope ?? ""),
    scenarioKey: typeof warning?.scenarioKey === "string" ? warning.scenarioKey : null,
    code: String(warning?.code ?? "UNKNOWN_WARNING"),
    affectedYearMonth: typeof warning?.affectedYearMonth === "string" ? warning.affectedYearMonth : null,
  };
}

function normalizeResult(payload, shared) {
  const series = normalizeBaselineSimulationPayload({ ...payload, ...shared });
  return {
    ...series,
    finalLiquidAssets: decimal(payload?.finalLiquidAssets),
    finalInvestmentAssets: decimal(payload?.finalInvestmentAssets),
    finalTotalFinancialAssets: decimal(payload?.finalTotalFinancialAssets),
    finalDebt: decimal(payload?.finalDebt),
    finalNetWorth: decimal(payload?.finalNetWorth),
    lastMonthDisposableCashFlow: decimal(payload?.lastMonthDisposableCashFlow),
    cashShortfall: payload?.cashShortfall === true,
    negativeAmortization: payload?.negativeAmortization === true,
  };
}

function normalizeCheckpoint(checkpoint) {
  return normalizeBaselineSimulationPayload({ checkpoints: [checkpoint] }).checkpoints[0] ?? null;
}

export function normalizeMultiScenarioComparison(text) {
  const payload = parsePrecisionJson(text);
  const shared = {
    financialProfileVersion: payload?.financialProfileVersion,
    startYearMonth: payload?.startYearMonth,
    horizonMonths: payload?.horizonMonths,
    assumptions: payload?.assumptions,
  };
  const scenarios = Array.isArray(payload?.scenarios) ? payload.scenarios.map((scenario) => ({
    ...normalizeResult(scenario, shared),
    scenarioKey: String(scenario?.scenarioKey ?? ""),
    label: String(scenario?.label ?? ""),
    normalizedEvents: Array.isArray(scenario?.normalizedEvents) ? scenario.normalizedEvents.map(normalizeEvent) : [],
    baselineDelta: normalizeDelta(scenario?.baselineDelta),
    residualDelta: decimal(scenario?.residualDelta),
    warnings: Array.isArray(scenario?.warnings) ? scenario.warnings.map(normalizeWarning) : [],
  })) : [];
  return {
    financialProfileVersion: Number(payload?.financialProfileVersion) || 0,
    startYearMonth: String(payload?.startYearMonth ?? ""),
    horizonMonths: Number(payload?.horizonMonths) || 0,
    assumptions: normalizeBaselineSimulationPayload(shared).assumptions,
    baseline: normalizeResult(payload?.baseline ?? {}, shared),
    scenarios,
    checkpointComparisons: Array.isArray(payload?.checkpointComparisons)
      ? payload.checkpointComparisons.map((checkpoint) => ({
        monthNumber: Number(checkpoint?.monthNumber) || 0,
        yearMonth: String(checkpoint?.yearMonth ?? ""),
        baseline: normalizeCheckpoint(checkpoint?.baseline),
        scenarios: Array.isArray(checkpoint?.scenarios) ? checkpoint.scenarios.map((scenario) => ({
          scenarioKey: String(scenario?.scenarioKey ?? ""),
          label: String(scenario?.label ?? ""),
          result: normalizeCheckpoint(scenario?.result),
          baselineDelta: normalizeDelta(scenario?.baselineDelta),
        })) : [],
      })) : [],
    calculationWarnings: Array.isArray(payload?.calculationWarnings)
      ? payload.calculationWarnings.map(normalizeWarning) : [],
    calculationBasis: {
      monthlyRateFormula: String(payload?.calculationBasis?.monthlyRateFormula ?? ""),
      moneyRounding: String(payload?.calculationBasis?.moneyRounding ?? ""),
      savingsTreatment: String(payload?.calculationBasis?.savingsTreatment ?? ""),
      investmentTreatment: String(payload?.calculationBasis?.investmentTreatment ?? ""),
    },
    disclaimer: String(payload?.disclaimer ?? ""),
  };
}

export function buildMultiScenarioPayload(values, scenarios) {
  return {
    ...buildSimulationRequestFields(values),
    scenarios: scenarios.map((scenario) => ({
      scenarioKey: scenario.scenarioKey,
      label: String(scenario.label ?? "").trim(),
      events: scenario.events.map(buildFinancialEventPayload),
    })),
  };
}

export function validateScenarioLab(values, scenarios) {
  const assumptionErrors = validateSimulationAssumptions(values);
  const scenarioErrors = {};
  if (!Array.isArray(scenarios) || scenarios.length < 1) scenarioErrors.scenarios = "비교할 Scenario를 한 개 이상 추가해주세요.";
  if (scenarios.length > 4) scenarioErrors.scenarios = "Scenario는 최대 4개까지 비교할 수 있습니다.";
  const keys = new Set();
  for (const scenario of scenarios) {
    const label = String(scenario.label ?? "").trim();
    if (!label || label.length > 100 || /[\u0000-\u001f\u007f]/.test(label)) {
      scenarioErrors[`${scenario.scenarioKey}.label`] = "이름은 제어문자 없이 1자 이상 100자 이하로 입력해주세요.";
    }
    if (keys.has(scenario.scenarioKey)) scenarioErrors[`${scenario.scenarioKey}.key`] = "Scenario 식별자가 중복됐습니다.";
    keys.add(scenario.scenarioKey);
    const eventErrors = validateFinancialEvents(scenario.events, values);
    Object.entries(eventErrors).forEach(([field, message]) => {
      scenarioErrors[`${scenario.scenarioKey}.${field}`] = message;
    });
  }
  return { assumptionErrors, scenarioErrors };
}

export async function compareMultipleScenarios(values, scenarios) {
  const text = await apiRequest("/api/simulations/compare-multiple", {
    method: "POST",
    body: buildMultiScenarioPayload(values, scenarios),
    responseType: "text",
  });
  return normalizeMultiScenarioComparison(text);
}
