import { apiRequest } from "./apiClient";
import { normalizeBaselineSimulationPayload } from "./baselineSimulationApi";
import {
  buildSimulationRequestFields,
  validateSimulationAssumptions,
} from "../simulation/simulationAssumptions";

const MONEY_PATTERN = /^\d{1,17}(?:\.\d{1,2})?$/;
const RATE_PATTERN = /^-?\d{1,3}(?:\.\d{1,6})?$/;
const YEAR_MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const DECIMAL_FIELDS = [
  "annualIncomeGrowthRate", "annualInflationRate", "annualDepositInterestRate",
  "annualInvestmentReturnRate", "monthlyDebtPayment", "domesticStockAmount",
  "overseasStockAmount", "otherInvestmentAssets", "domesticStockShockRate",
  "overseasStockShockRate", "krwUsdExchangeRateShockRate",
  "loanInterestRateChangePercentagePoints", "targetNetWorth", "income", "fixedExpenses",
  "variableExpenses", "oneTimeExpense", "debtInterest", "debtPayment", "extraDebtRepayment",
  "principalRepaid", "savingsAllocation", "investmentContribution", "depositInterest",
  "investmentReturn", "disposableCashFlow", "liquidAssets", "investmentAssets",
  "totalFinancialAssets", "remainingDebt", "netWorth", "consumption", "savingsAllocated",
  "investmentContributions", "domesticExposureAtShock", "domesticStockImpact",
  "overseasExposureAtShock", "overseasStockImpact", "exchangeRateImpact",
  "totalInvestmentImpact", "additionalDebtInterest", "finalNetWorthDelta",
  "minimumLiquidAssets", "finalRemainingDebt", "baselineFinalNetWorth",
  "stressedFinalNetWorth", "baselineMargin", "stressedMargin", "marginDelta", "value",
];
const DECIMAL_NUMBER_PATTERN = new RegExp(
  `(\\"(?:${DECIMAL_FIELDS.join("|")})\\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`, "g",
);

function decimalText(value) {
  return value == null ? "0" : String(value);
}

function nullableDecimalText(value) {
  return value == null ? null : String(value);
}

function parsePrecisionJson(text) {
  return JSON.parse(text.replace(DECIMAL_NUMBER_PATTERN, '$1"$2"'));
}

function monthIndex(value) {
  if (!YEAR_MONTH_PATTERN.test(String(value ?? ""))) return null;
  const [year, month] = value.split("-").map(Number);
  return year * 12 + month - 1;
}

function decimalToMinor(value) {
  const text = String(value ?? "").trim();
  if (!MONEY_PATTERN.test(text)) return null;
  const [whole, fraction = ""] = text.split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}

function validRate(value, minimum, maximum) {
  const text = String(value ?? "").trim();
  if (!RATE_PATTERN.test(text)) return false;
  const numeric = Number(text);
  return Number.isFinite(numeric) && numeric >= minimum && numeric <= maximum;
}

export function createMarketStressValues() {
  const start = new Date();
  const startYearMonth = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, "0")}`;
  const shock = new Date(start.getFullYear(), start.getMonth() + 11, 1);
  return {
    startYearMonth,
    horizonMonths: 60,
    annualIncomeGrowthRate: "0",
    annualInflationRate: "0",
    annualDepositInterestRate: "0",
    annualInvestmentReturnRate: "0",
    monthlyDebtPayment: "0",
    domesticStockAmount: "0",
    overseasStockAmount: "0",
    shockYearMonth: `${shock.getFullYear()}-${String(shock.getMonth() + 1).padStart(2, "0")}`,
    domesticStockShockRate: "-20",
    overseasStockShockRate: "-20",
    krwUsdExchangeRateShockRate: "0",
    loanInterestRateChangePercentagePoints: "2",
    targetNetWorth: "",
  };
}

export function buildMarketStressPayload(values) {
  const simulation = buildSimulationRequestFields(values);
  const target = String(values.targetNetWorth ?? "").trim();
  return {
    ...simulation,
    exposure: {
      domesticStockAmount: String(values.domesticStockAmount ?? "").trim(),
      overseasStockAmount: String(values.overseasStockAmount ?? "").trim(),
    },
    stressScenario: {
      shockYearMonth: String(values.shockYearMonth ?? "").trim(),
      domesticStockShockRate: String(values.domesticStockShockRate ?? "").trim(),
      overseasStockShockRate: String(values.overseasStockShockRate ?? "").trim(),
      krwUsdExchangeRateShockRate: String(values.krwUsdExchangeRateShockRate ?? "").trim(),
      loanInterestRateChangePercentagePoints:
        String(values.loanInterestRateChangePercentagePoints ?? "").trim(),
    },
    targetNetWorth: target || null,
  };
}

export function validateMarketStress(values, profile) {
  const errors = validateSimulationAssumptions(values);
  const domestic = decimalToMinor(values.domesticStockAmount);
  const overseas = decimalToMinor(values.overseasStockAmount);
  if (domestic == null) errors.domesticStockAmount = "0원 이상, 소수점 2자리까지 입력해주세요.";
  if (overseas == null) errors.overseasStockAmount = "0원 이상, 소수점 2자리까지 입력해주세요.";
  const investmentAssets = decimalToMinor(String(profile?.investmentAssets ?? "0"));
  if (domestic != null && overseas != null && investmentAssets != null
      && domestic + overseas > investmentAssets) {
    errors.exposure = "국내·해외 주식 Exposure 합계는 현재 투자자산을 초과할 수 없습니다.";
  }
  if (!YEAR_MONTH_PATTERN.test(String(values.shockYearMonth ?? ""))) {
    errors.shockYearMonth = "충격 연월을 선택해주세요.";
  } else {
    const start = monthIndex(values.startYearMonth);
    const shock = monthIndex(values.shockYearMonth);
    const end = start == null ? null : start + Number(values.horizonMonths) - 1;
    if (start != null && (shock < start || shock > end)) {
      errors.shockYearMonth = "충격 연월은 시뮬레이션 기간 안에 있어야 합니다.";
    }
  }
  if (!validRate(values.domesticStockShockRate, -100, 0)) {
    errors.domesticStockShockRate = "-100%부터 0% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!validRate(values.overseasStockShockRate, -100, 0)) {
    errors.overseasStockShockRate = "-100%부터 0% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!validRate(values.krwUsdExchangeRateShockRate, -100, 100)) {
    errors.krwUsdExchangeRateShockRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!validRate(values.loanInterestRateChangePercentagePoints, -20, 20)) {
    errors.loanInterestRateChangePercentagePoints = "-20%p부터 20%p 사이로 입력해주세요.";
  }
  const targetText = String(values.targetNetWorth ?? "").trim();
  if (targetText) {
    const target = decimalToMinor(targetText);
    if (target == null || target <= 0n) errors.targetNetWorth = "목표 순자산은 0원보다 커야 합니다.";
  }
  return errors;
}

function normalizeRun(payload, root) {
  const normalized = normalizeBaselineSimulationPayload({
    financialProfileVersion: root.financialProfileVersion,
    startYearMonth: root.startYearMonth,
    horizonMonths: root.horizonMonths,
    assumptions: root.assumptions,
    monthlyResults: payload?.monthlyResults,
    checkpoints: payload?.checkpoints,
    finalCumulativeTotals: payload?.finalCumulativeTotals,
    calculationBasis: {},
  });
  return {
    monthlyResults: normalized.monthlyResults,
    checkpoints: normalized.checkpoints,
    finalCumulativeTotals: normalized.finalCumulativeTotals,
  };
}

function normalizeRisk(payload) {
  return {
    cashShortfall: payload?.cashShortfall === true,
    cashShortfallMonthCount: Number(payload?.cashShortfallMonthCount) || 0,
    firstCashShortfallMonth: String(payload?.firstCashShortfallMonth ?? ""),
    negativeAmortization: payload?.negativeAmortization === true,
    negativeAmortizationMonthCount: Number(payload?.negativeAmortizationMonthCount) || 0,
    firstNegativeAmortizationMonth: String(payload?.firstNegativeAmortizationMonth ?? ""),
    minimumLiquidAssets: decimalText(payload?.minimumLiquidAssets),
    finalRemainingDebt: decimalText(payload?.finalRemainingDebt),
  };
}

export function normalizeMarketStress(text) {
  const payload = parsePrecisionJson(text);
  return {
    financialProfileVersion: Number(payload?.financialProfileVersion) || 0,
    startYearMonth: String(payload?.startYearMonth ?? ""),
    horizonMonths: Number(payload?.horizonMonths) || 0,
    assumptions: Object.fromEntries(Object.entries(payload?.assumptions ?? {})
      .map(([key, value]) => [key, decimalText(value)])),
    exposure: Object.fromEntries(Object.entries(payload?.exposure ?? {})
      .map(([key, value]) => [key, decimalText(value)])),
    stressScenario: Object.fromEntries(Object.entries(payload?.stressScenario ?? {})
      .map(([key, value]) => [key, key === "shockYearMonth" ? String(value) : decimalText(value)])),
    marketContextUsage: {
      usedInCalculation: payload?.marketContextUsage?.usedInCalculation === true,
      boundary: String(payload?.marketContextUsage?.boundary ?? ""),
    },
    baseline: normalizeRun(payload?.baseline, payload),
    stressed: normalizeRun(payload?.stressed, payload),
    marketImpactBreakdown: Object.fromEntries(Object.entries(payload?.marketImpactBreakdown ?? {})
      .map(([key, value]) => [key, key === "shockYearMonth" ? String(value) : decimalText(value)])),
    riskComparison: {
      baseline: normalizeRisk(payload?.riskComparison?.baseline),
      stressed: normalizeRisk(payload?.riskComparison?.stressed),
      newCashShortfall: payload?.riskComparison?.newCashShortfall === true,
      newNegativeAmortization: payload?.riskComparison?.newNegativeAmortization === true,
    },
    goalMarginComparison: {
      status: String(payload?.goalMarginComparison?.status ?? "NOT_PROVIDED"),
      targetNetWorth: nullableDecimalText(payload?.goalMarginComparison?.targetNetWorth),
      baselineFinalNetWorth: decimalText(payload?.goalMarginComparison?.baselineFinalNetWorth),
      stressedFinalNetWorth: decimalText(payload?.goalMarginComparison?.stressedFinalNetWorth),
      baselineMargin: nullableDecimalText(payload?.goalMarginComparison?.baselineMargin),
      stressedMargin: nullableDecimalText(payload?.goalMarginComparison?.stressedMargin),
      marginDelta: nullableDecimalText(payload?.goalMarginComparison?.marginDelta),
    },
    warnings: Array.isArray(payload?.warnings) ? payload.warnings.map((item) => ({
      code: String(item?.code ?? "UNKNOWN"),
    })) : [],
    calculationBasis: Object.fromEntries(Object.entries(payload?.calculationBasis ?? {})
      .map(([key, value]) => [key, String(value ?? "")])),
    disclaimer: String(payload?.disclaimer ?? ""),
  };
}

export function normalizeMarketContext(text) {
  const payload = parsePrecisionJson(text);
  return {
    status: String(payload?.status ?? "UNAVAILABLE"),
    checkedAt: String(payload?.checkedAt ?? ""),
    observations: Array.isArray(payload?.observations) ? payload.observations.map((item) => ({
      indicator: String(item?.indicator ?? ""),
      value: nullableDecimalText(item?.value),
      unit: String(item?.unit ?? ""),
      observedOn: String(item?.observedOn ?? ""),
      status: String(item?.status ?? "UNAVAILABLE"),
      issueCode: String(item?.issueCode ?? "DATA_NOT_FOUND"),
      source: String(item?.source ?? ""),
    })) : [],
    usageBoundary: String(payload?.usageBoundary ?? ""),
  };
}

export async function getMarketContext() {
  const text = await apiRequest("/api/market-stress/context", { responseType: "text" });
  return normalizeMarketContext(text);
}

export async function runMarketStress(values) {
  const text = await apiRequest("/api/market-stress/simulate", {
    method: "POST",
    body: buildMarketStressPayload(values),
    responseType: "text",
  });
  return normalizeMarketStress(text);
}
