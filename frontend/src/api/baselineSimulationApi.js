import { apiRequest } from "./apiClient";

const RATE_FIELDS = Object.freeze([
  "annualIncomeGrowthRate",
  "annualInflationRate",
  "annualDepositInterestRate",
  "annualInvestmentReturnRate",
]);

const MONEY_FIELDS = Object.freeze([
  "monthlyDebtPayment",
  "income",
  "fixedExpenses",
  "variableExpenses",
  "oneTimeExpense",
  "debtInterest",
  "debtPayment",
  "extraDebtRepayment",
  "principalRepaid",
  "savingsAllocation",
  "investmentContribution",
  "depositInterest",
  "investmentReturn",
  "disposableCashFlow",
  "liquidAssets",
  "investmentAssets",
  "totalFinancialAssets",
  "remainingDebt",
  "netWorth",
  "consumption",
  "savingsAllocated",
  "investmentContributions",
]);

const DECIMAL_FIELDS = Object.freeze([...RATE_FIELDS, ...MONEY_FIELDS]);
const DECIMAL_NUMBER_PATTERN = new RegExp(
  `(\\"(?:${DECIMAL_FIELDS.join("|")})\\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`,
  "g",
);
const YEAR_MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const RATE_PATTERN = /^-?\d{1,3}(?:\.\d{1,6})?$/;
const MONEY_PATTERN = /^\d{1,17}(?:\.\d{1,2})?$/;
const HORIZONS = new Set([12, 36, 60]);

function decimalText(value) {
  return value == null ? "0" : String(value);
}

function normalizeCumulativeTotals(payload) {
  return {
    income: decimalText(payload?.income),
    consumption: decimalText(payload?.consumption),
    debtInterest: decimalText(payload?.debtInterest),
    principalRepaid: decimalText(payload?.principalRepaid),
    savingsAllocated: decimalText(payload?.savingsAllocated),
    investmentContributions: decimalText(payload?.investmentContributions),
    investmentReturn: decimalText(payload?.investmentReturn),
  };
}

function normalizeMonthlyResult(payload) {
  return {
    monthNumber: Number(payload?.monthNumber) || 0,
    yearMonth: typeof payload?.yearMonth === "string" ? payload.yearMonth : "",
    income: decimalText(payload?.income),
    fixedExpenses: decimalText(payload?.fixedExpenses),
    variableExpenses: decimalText(payload?.variableExpenses),
    oneTimeExpense: decimalText(payload?.oneTimeExpense),
    debtInterest: decimalText(payload?.debtInterest),
    debtPayment: decimalText(payload?.debtPayment),
    extraDebtRepayment: decimalText(payload?.extraDebtRepayment),
    principalRepaid: decimalText(payload?.principalRepaid),
    savingsAllocation: decimalText(payload?.savingsAllocation),
    investmentContribution: decimalText(payload?.investmentContribution),
    depositInterest: decimalText(payload?.depositInterest),
    investmentReturn: decimalText(payload?.investmentReturn),
    disposableCashFlow: decimalText(payload?.disposableCashFlow),
    liquidAssets: decimalText(payload?.liquidAssets),
    investmentAssets: decimalText(payload?.investmentAssets),
    totalFinancialAssets: decimalText(payload?.totalFinancialAssets),
    remainingDebt: decimalText(payload?.remainingDebt),
    netWorth: decimalText(payload?.netWorth),
    cashShortfall: payload?.cashShortfall === true,
    negativeAmortization: payload?.negativeAmortization === true,
    cumulativeTotals: normalizeCumulativeTotals(payload?.cumulativeTotals),
  };
}

function normalizeCheckpoint(payload) {
  return {
    monthNumber: Number(payload?.monthNumber) || 0,
    yearMonth: typeof payload?.yearMonth === "string" ? payload.yearMonth : "",
    liquidAssets: decimalText(payload?.liquidAssets),
    investmentAssets: decimalText(payload?.investmentAssets),
    totalFinancialAssets: decimalText(payload?.totalFinancialAssets),
    remainingDebt: decimalText(payload?.remainingDebt),
    netWorth: decimalText(payload?.netWorth),
    cumulativeTotals: normalizeCumulativeTotals(payload?.cumulativeTotals),
  };
}

function parseSimulationJson(text) {
  const precisionSafeText = text.replace(DECIMAL_NUMBER_PATTERN, '$1"$2"');
  return JSON.parse(precisionSafeText);
}

export function normalizeBaselineSimulation(text) {
  const payload = parseSimulationJson(text);
  return {
    financialProfileVersion: Number(payload?.financialProfileVersion) || 0,
    startYearMonth: typeof payload?.startYearMonth === "string" ? payload.startYearMonth : "",
    horizonMonths: Number(payload?.horizonMonths) || 0,
    assumptions: Object.fromEntries(
      [...RATE_FIELDS, "monthlyDebtPayment"].map((field) => [field, decimalText(payload?.assumptions?.[field])]),
    ),
    monthlyResults: Array.isArray(payload?.monthlyResults)
      ? payload.monthlyResults.map(normalizeMonthlyResult)
      : [],
    checkpoints: Array.isArray(payload?.checkpoints)
      ? payload.checkpoints.map(normalizeCheckpoint)
      : [],
    finalCumulativeTotals: normalizeCumulativeTotals(payload?.finalCumulativeTotals),
    calculationBasis: {
      monthlyRateFormula: String(payload?.calculationBasis?.monthlyRateFormula ?? ""),
      moneyRounding: String(payload?.calculationBasis?.moneyRounding ?? ""),
      savingsTreatment: String(payload?.calculationBasis?.savingsTreatment ?? ""),
      investmentTreatment: String(payload?.calculationBasis?.investmentTreatment ?? ""),
      disclaimer: String(payload?.calculationBasis?.disclaimer ?? ""),
    },
  };
}

export function buildBaselineSimulationPayload(values) {
  return {
    startYearMonth: String(values.startYearMonth ?? "").trim(),
    horizonMonths: Number(values.horizonMonths),
    assumptions: {
      annualIncomeGrowthRate: String(values.annualIncomeGrowthRate ?? "").trim(),
      annualInflationRate: String(values.annualInflationRate ?? "").trim(),
      annualDepositInterestRate: String(values.annualDepositInterestRate ?? "").trim(),
      annualInvestmentReturnRate: String(values.annualInvestmentReturnRate ?? "").trim(),
      monthlyDebtPayment: String(values.monthlyDebtPayment ?? "").trim(),
    },
  };
}

function validateRate(value, minimum) {
  const text = String(value ?? "").trim();
  if (!RATE_PATTERN.test(text)) return false;
  const number = Number(text);
  return Number.isFinite(number) && number >= minimum && number <= 100;
}

export function validateBaselineSimulation(values) {
  const errors = {};
  if (!YEAR_MONTH_PATTERN.test(String(values.startYearMonth ?? ""))) {
    errors.startYearMonth = "시작 연월을 선택해주세요.";
  }
  if (!HORIZONS.has(Number(values.horizonMonths))) {
    errors.horizonMonths = "기간은 12, 36, 60개월 중 하나여야 합니다.";
  }
  if (!validateRate(values.annualIncomeGrowthRate, -100)) {
    errors.annualIncomeGrowthRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!validateRate(values.annualInflationRate, -100)) {
    errors.annualInflationRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!validateRate(values.annualDepositInterestRate, 0)) {
    errors.annualDepositInterestRate = "0%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!validateRate(values.annualInvestmentReturnRate, -100)) {
    errors.annualInvestmentReturnRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  }
  if (!MONEY_PATTERN.test(String(values.monthlyDebtPayment ?? "").trim())) {
    errors.monthlyDebtPayment = "0원 이상, 소수점 2자리까지 입력해주세요.";
  }
  return errors;
}

export async function runBaselineSimulation(values) {
  const text = await apiRequest("/api/simulations/baseline", {
    method: "POST",
    body: buildBaselineSimulationPayload(values),
    responseType: "text",
  });
  return normalizeBaselineSimulation(text);
}
