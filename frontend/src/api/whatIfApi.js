import { apiRequest } from "./apiClient";
import { normalizeBaselineSimulationPayload } from "./baselineSimulationApi";
import { buildFinancialEventPayload } from "../simulation/financialEvents";
import { buildSimulationRequestFields } from "../simulation/simulationAssumptions";

const DECIMAL_FIELDS = [
  "annualIncomeGrowthRate", "annualInflationRate", "annualDepositInterestRate",
  "annualInvestmentReturnRate", "monthlyDebtPayment", "amount", "monthlyDelta",
  "income", "fixedExpenses", "variableExpenses", "oneTimeExpense", "debtInterest",
  "debtPayment", "extraDebtRepayment", "principalRepaid", "savingsAllocation",
  "investmentContribution", "depositInterest", "investmentReturn", "disposableCashFlow",
  "liquidAssets", "investmentAssets", "totalFinancialAssets", "remainingDebt", "netWorth",
  "consumption", "savingsAllocated", "investmentContributions", "liquidAssetsDelta",
  "investmentAssetsDelta", "totalFinancialAssetsDelta", "debtDelta", "netWorthDelta",
  "cumulativeIncomeDelta", "cumulativeConsumptionDelta", "cumulativeDebtInterestDelta",
  "cumulativePrincipalRepaidDelta", "cumulativeInvestmentContributionDelta",
  "cumulativeInvestmentReturnDelta", "incomeDelta", "consumptionDelta", "debtInterestDelta",
  "principalRepaidDelta", "investmentContributionDelta", "investmentReturnDelta", "residualDelta",
  "baselineFinalNetWorth", "whatIfFinalNetWorth",
];
const DECIMAL_PATTERN = new RegExp(`(\\"(?:${DECIMAL_FIELDS.join("|")})\\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`, "g");

function parsePrecisionJson(text) {
  return JSON.parse(text.replace(DECIMAL_PATTERN, '$1"$2"'));
}

function decimal(value) {
  return value == null ? "0" : String(value);
}

function optionalDecimal(value) {
  return value == null ? null : String(value);
}

function normalizeEvent(event) {
  return {
    eventId: String(event?.eventId ?? ""),
    eventType: String(event?.eventType ?? ""),
    effectiveYearMonth: typeof event?.effectiveYearMonth === "string" ? event.effectiveYearMonth : null,
    startYearMonth: typeof event?.startYearMonth === "string" ? event.startYearMonth : null,
    endYearMonth: typeof event?.endYearMonth === "string" ? event.endYearMonth : null,
    amount: optionalDecimal(event?.amount),
    monthlyDelta: optionalDecimal(event?.monthlyDelta),
    description: String(event?.description ?? ""),
  };
}

function normalizeComparison(result) {
  return {
    monthNumber: Number(result?.monthNumber) || 0,
    yearMonth: String(result?.yearMonth ?? ""),
    liquidAssetsDelta: decimal(result?.liquidAssetsDelta),
    investmentAssetsDelta: decimal(result?.investmentAssetsDelta),
    totalFinancialAssetsDelta: decimal(result?.totalFinancialAssetsDelta),
    debtDelta: decimal(result?.debtDelta),
    netWorthDelta: decimal(result?.netWorthDelta),
    cumulativeIncomeDelta: decimal(result?.cumulativeIncomeDelta),
    cumulativeConsumptionDelta: decimal(result?.cumulativeConsumptionDelta),
    cumulativeDebtInterestDelta: decimal(result?.cumulativeDebtInterestDelta),
    cumulativePrincipalRepaidDelta: decimal(result?.cumulativePrincipalRepaidDelta),
    cumulativeInvestmentContributionDelta: decimal(result?.cumulativeInvestmentContributionDelta),
    cumulativeInvestmentReturnDelta: decimal(result?.cumulativeInvestmentReturnDelta),
  };
}

function normalizeImpact(result) {
  const fields = ["incomeDelta", "consumptionDelta", "debtInterestDelta", "principalRepaidDelta",
    "investmentContributionDelta", "investmentReturnDelta", "liquidAssetsDelta", "debtDelta",
    "netWorthDelta", "residualDelta"];
  return Object.fromEntries(fields.map((field) => [field, decimal(result?.[field])]));
}

export function normalizeScenarioComparisonPayload(payload) {
  const sharedSeriesFields = {
    financialProfileVersion: payload?.financialProfileVersion,
    startYearMonth: payload?.startYearMonth,
    horizonMonths: payload?.horizonMonths,
    assumptions: payload?.assumptions,
  };
  return {
    financialProfileVersion: Number(payload?.financialProfileVersion) || 0,
    scenarioName: String(payload?.scenarioName ?? ""),
    startYearMonth: String(payload?.startYearMonth ?? ""),
    horizonMonths: Number(payload?.horizonMonths) || 0,
    assumptions: normalizeBaselineSimulationPayload({ assumptions: payload?.assumptions }).assumptions,
    normalizedEvents: Array.isArray(payload?.normalizedEvents) ? payload.normalizedEvents.map(normalizeEvent) : [],
    baseline: normalizeBaselineSimulationPayload({ ...payload?.baseline, ...sharedSeriesFields }),
    whatIf: normalizeBaselineSimulationPayload({ ...payload?.whatIf, ...sharedSeriesFields }),
    checkpointComparisons: Array.isArray(payload?.checkpointComparisons) ? payload.checkpointComparisons.map(normalizeComparison) : [],
    finalComparison: normalizeComparison(payload?.finalComparison),
    impactSummary: normalizeImpact(payload?.impactSummary),
    warnings: Array.isArray(payload?.calculationWarnings)
      ? payload.calculationWarnings.filter((item) => typeof item === "string")
      : Array.isArray(payload?.warnings) ? payload.warnings.filter((item) => typeof item === "string") : [],
  };
}

export function normalizeScenarioComparison(text) {
  return normalizeScenarioComparisonPayload(parsePrecisionJson(text));
}

export function normalizePrivacyPreview(text) {
  const payload = JSON.parse(text);
  const external = payload?.externalPayload;
  const externalPayload = external ? {
    schemaVersion: String(external.schemaVersion ?? ""), purpose: String(external.purpose ?? ""),
    locale: String(external.locale ?? ""), currentYearMonth: String(external.currentYearMonth ?? ""),
    sanitizedScenarioText: String(external.sanitizedScenarioText ?? ""),
    supportedEventTypes: Array.isArray(external.supportedEventTypes) ? external.supportedEventTypes.map(String) : [],
    supportedReferenceTypes: Array.isArray(external.supportedReferenceTypes) ? external.supportedReferenceTypes.map(String) : [],
    outputContractVersion: String(external.outputContractVersion ?? ""),
  } : null;
  return {
    status: String(payload?.status ?? ""),
    privacyMode: "STRICT",
    externalPayload,
    externalFieldNames: externalPayload ? Object.keys(externalPayload) : [],
    referenceTypes: [...new Set((Array.isArray(payload?.references) ? payload.references : [])
      .map((item) => item?.referenceType).filter((item) => typeof item === "string"))],
    blockedIdentifierTypes: Array.isArray(payload?.blockedIdentifierTypes) ? payload.blockedIdentifierTypes.map(String) : [],
    privacyNotice: String(payload?.privacyNotice ?? ""),
  };
}

function normalizeAgentTypedResult(result) {
  if (!result || typeof result !== "object") return null;
  return {
    startYearMonth: String(result.startYearMonth ?? ""), horizonMonths: Number(result.horizonMonths) || 0,
    finalYearMonth: String(result.finalYearMonth ?? ""), baselineFinalNetWorth: decimal(result.baselineFinalNetWorth),
    whatIfFinalNetWorth: decimal(result.whatIfFinalNetWorth), netWorthDelta: decimal(result.netWorthDelta),
    liquidAssetsDelta: decimal(result.liquidAssetsDelta), debtDelta: decimal(result.debtDelta),
    cumulativeIncomeDelta: decimal(result.cumulativeIncomeDelta), cumulativeConsumptionDelta: decimal(result.cumulativeConsumptionDelta),
    cashShortfallMonths: Array.isArray(result.cashShortfallMonths) ? result.cashShortfallMonths.map(String) : [],
    negativeAmortizationMonths: Array.isArray(result.negativeAmortizationMonths) ? result.negativeAmortizationMonths.map(String) : [],
    serviceWarnings: Array.isArray(result.serviceWarnings) ? result.serviceWarnings.map(String) : [],
    comparisonDetails: result.comparisonDetails
      ? normalizeScenarioComparisonPayload({ scenarioName: "자연어 What-if", ...result.comparisonDetails })
      : null,
  };
}

export function normalizeNaturalLanguageResponse(text) {
  const payload = parsePrecisionJson(text);
  return {
    status: String(payload?.agentStatus ?? ""),
    resultType: typeof payload?.resultType === "string" ? payload.resultType : null,
    typedResult: normalizeAgentTypedResult(payload?.typedResult),
    missingInformation: Array.isArray(payload?.missingInformation) ? payload.missingInformation.map((item) => ({
      code: String(item?.code ?? ""), field: String(item?.field ?? ""), question: String(item?.question ?? ""),
      requiredForIntent: String(item?.requiredForIntent ?? ""),
    })) : [],
    clarificationQuestions: Array.isArray(payload?.clarificationQuestions) ? payload.clarificationQuestions.map(String) : [],
    risks: Array.isArray(payload?.risks) ? payload.risks.map((risk) => ({
      code: String(risk?.code ?? ""), severity: String(risk?.severity ?? ""),
      evidenceField: String(risk?.evidenceField ?? ""), affectedYearMonth: risk?.affectedYearMonth == null ? null : String(risk.affectedYearMonth),
      summary: String(risk?.summary ?? ""),
    })) : [],
    explanation: payload?.explanation ? {
      headline: String(payload.explanation.headline ?? ""), summary: String(payload.explanation.summary ?? ""),
      evidence: Array.isArray(payload.explanation.evidence) ? payload.explanation.evidence.map((item) => ({ fieldPath: String(item?.fieldPath ?? ""), value: String(item?.value ?? "") })) : [],
      assumptionNotice: String(payload.explanation.assumptionNotice ?? ""), disclaimer: String(payload.explanation.disclaimer ?? ""),
    } : null,
    trace: Array.isArray(payload?.trace) ? payload.trace.map((step) => ({ sequence: Number(step?.sequence) || 0,
      state: String(step?.state ?? ""), component: String(step?.component ?? ""), outcomeCode: String(step?.outcomeCode ?? "") })) : [],
    metadata: {
      aiUsed: payload?.aiUsed === true, provider: typeof payload?.provider === "string" ? payload.provider : null,
      model: typeof payload?.model === "string" ? payload.model : null,
      privacyMode: typeof payload?.privacyMode === "string" ? payload.privacyMode : null,
      financialValuesTokenized: payload?.financialValuesTokenized === true,
      toolCallCount: Number(payload?.toolCallCount) || 0,
    },
    privacyNotice: String(payload?.privacyNotice ?? ""), disclaimer: String(payload?.disclaimer ?? ""),
  };
}

export function buildScenarioComparisonPayload(scenarioName, values, events) {
  return { scenarioName: String(scenarioName ?? "").trim(), ...buildSimulationRequestFields(values), events: events.map(buildFinancialEventPayload) };
}

export async function previewScenarioPayload(scenarioText) {
  const text = await apiRequest("/api/privacy/scenario-payload-preview", { method: "POST", body: { scenarioText }, responseType: "text" });
  return normalizePrivacyPreview(text);
}

export async function runNaturalLanguageWhatIf(scenarioText, values) {
  const text = await apiRequest("/api/agent/natural-language", { method: "POST", body: { scenarioText, ...buildSimulationRequestFields(values) }, responseType: "text" });
  return normalizeNaturalLanguageResponse(text);
}

export async function compareScenario(scenarioName, values, events) {
  const text = await apiRequest("/api/simulations/compare", { method: "POST", body: buildScenarioComparisonPayload(scenarioName, values, events), responseType: "text" });
  return normalizeScenarioComparison(text);
}
