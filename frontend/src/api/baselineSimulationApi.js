import { apiRequest } from "./apiClient";
import { buildSimulationRequestFields, validateSimulationAssumptions } from "../simulation/simulationAssumptions";

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
  return normalizeBaselineSimulationPayload(parseSimulationJson(text));
}

export function normalizeBaselineSimulationPayload(payload) {
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
  return buildSimulationRequestFields(values);
}

export function validateBaselineSimulation(values) {
  return validateSimulationAssumptions(values);
}

export async function runBaselineSimulation(values) {
  const text = await apiRequest("/api/simulations/baseline", {
    method: "POST",
    body: buildBaselineSimulationPayload(values),
    responseType: "text",
  });
  return normalizeBaselineSimulation(text);
}
