import { ApiError, apiRequest } from "./apiClient";
import { normalizeBaselineSimulationPayload } from "./baselineSimulationApi";
import {
  buildSimulationRequestFields,
  profileHasDebt,
  validateSimulationAssumptions,
} from "../simulation/simulationAssumptions";

const TARGET_AMOUNT_PATTERN = /^\d{1,17}(?:\.\d{1,2})?$/;
const SUPPORTED_HORIZONS = new Set([12, 36, 60]);
const DECIMAL_FIELDS = [
  "targetAmount", "currentNetWorth", "baselineFinalNetWorth", "goalGap",
  "requiredMonthlyAmount", "maximumMonthlyAmountTested", "projectedFinalNetWorth", "goalMargin",
  "searchResolution", "incomeSearchUpperLimit", "amount", "monthlyDelta",
  "annualIncomeGrowthRate", "annualInflationRate", "annualDepositInterestRate",
  "annualInvestmentReturnRate", "monthlyDebtPayment", "income", "fixedExpenses",
  "variableExpenses", "oneTimeExpense", "debtInterest", "debtPayment",
  "extraDebtRepayment", "principalRepaid", "savingsAllocation", "investmentContribution",
  "depositInterest", "investmentReturn", "disposableCashFlow", "liquidAssets",
  "investmentAssets", "totalFinancialAssets", "remainingDebt", "netWorth",
  "consumption", "savingsAllocated", "investmentContributions",
];
const DECIMAL_PATTERN = new RegExp(
  `(\\"(?:${DECIMAL_FIELDS.join("|")})\\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`,
  "g",
);

function decimal(value) {
  return value == null ? "0" : String(value);
}

function optionalDecimal(value) {
  return value == null ? null : String(value);
}

function parsePrecisionJson(text) {
  return JSON.parse(text.replace(DECIMAL_PATTERN, '$1"$2"'));
}

function normalizeWarning(warning) {
  return { code: String(warning?.code ?? "UNKNOWN") };
}

function normalizeEvent(event) {
  return {
    eventType: String(event?.eventType ?? ""),
    effectiveYearMonth: typeof event?.effectiveYearMonth === "string" ? event.effectiveYearMonth : null,
    startYearMonth: typeof event?.startYearMonth === "string" ? event.startYearMonth : null,
    endYearMonth: typeof event?.endYearMonth === "string" ? event.endYearMonth : null,
    amount: optionalDecimal(event?.amount),
    monthlyDelta: optionalDecimal(event?.monthlyDelta),
    description: String(event?.description ?? ""),
  };
}

function normalizePlan(plan) {
  return {
    planType: String(plan?.planType ?? ""),
    planStatus: String(plan?.planStatus ?? ""),
    requiredMonthlyAmount: optionalDecimal(plan?.requiredMonthlyAmount),
    maximumMonthlyAmountTested: decimal(plan?.maximumMonthlyAmountTested),
    generatedEvents: Array.isArray(plan?.generatedEvents) ? plan.generatedEvents.map(normalizeEvent) : [],
    projectedFinalNetWorth: decimal(plan?.projectedFinalNetWorth),
    goalMargin: decimal(plan?.goalMargin),
    firstAchievedYearMonth: typeof plan?.firstAchievedYearMonth === "string"
      ? plan.firstAchievedYearMonth
      : null,
    achieved: plan?.achieved === true,
    solverIterations: Number.isInteger(plan?.solverIterations) ? plan.solverIterations : 0,
    appliedConstraints: Array.isArray(plan?.appliedConstraints)
      ? plan.appliedConstraints.filter((item) => typeof item === "string")
      : [],
    warnings: Array.isArray(plan?.warnings) ? plan.warnings.map(normalizeWarning) : [],
    projectedResult: normalizeBaselineSimulationPayload(plan?.projectedResult ?? {}),
  };
}

export function normalizeGoalReverseSimulation(text) {
  const payload = parsePrecisionJson(text);
  return {
    financialProfileVersion: Number.isInteger(payload?.financialProfileVersion)
      ? payload.financialProfileVersion
      : 0,
    goalType: String(payload?.goalType ?? ""),
    targetAmount: decimal(payload?.targetAmount),
    startYearMonth: String(payload?.startYearMonth ?? ""),
    targetEndYearMonth: String(payload?.targetEndYearMonth ?? ""),
    horizonMonths: Number(payload?.horizonMonths) || 0,
    assumptions: normalizeBaselineSimulationPayload({ assumptions: payload?.assumptions }).assumptions,
    goalStatus: String(payload?.goalStatus ?? ""),
    currentNetWorth: decimal(payload?.currentNetWorth),
    baselineFinalNetWorth: decimal(payload?.baselineFinalNetWorth),
    goalGap: decimal(payload?.goalGap),
    baselineFirstAchievedYearMonth: typeof payload?.baselineFirstAchievedYearMonth === "string"
      ? payload.baselineFirstAchievedYearMonth
      : null,
    baseline: normalizeBaselineSimulationPayload(payload?.baseline ?? {}),
    plans: Array.isArray(payload?.plans) ? payload.plans.map(normalizePlan) : [],
    solverMetadata: {
      searchResolution: decimal(payload?.solverMetadata?.searchResolution),
      maximumIterationsPerPlan: Number.isInteger(payload?.solverMetadata?.maximumIterationsPerPlan)
        ? payload.solverMetadata.maximumIterationsPerPlan
        : 0,
      incomeSearchUpperLimit: decimal(payload?.solverMetadata?.incomeSearchUpperLimit),
      totalIterations: Number.isInteger(payload?.solverMetadata?.totalIterations)
        ? payload.solverMetadata.totalIterations
        : 0,
      searchAlgorithm: String(payload?.solverMetadata?.searchAlgorithm ?? ""),
      monotonicityBasis: String(payload?.solverMetadata?.monotonicityBasis ?? ""),
    },
    warnings: Array.isArray(payload?.warnings) ? payload.warnings.map(normalizeWarning) : [],
    disclaimer: String(payload?.disclaimer ?? ""),
  };
}

function positiveMoney(value) {
  const text = String(value ?? "").trim();
  if (!TARGET_AMOUNT_PATTERN.test(text)) return false;
  const [integer, fraction = ""] = text.split(".");
  return BigInt(integer) * 100n + BigInt((fraction + "00").slice(0, 2)) > 0n;
}

export function goalTargetEndYearMonth(startYearMonth, horizonMonths) {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(String(startYearMonth ?? ""))) return "";
  if (!SUPPORTED_HORIZONS.has(Number(horizonMonths))) return "";
  const [year, month] = String(startYearMonth).split("-").map(Number);
  const end = new Date(Date.UTC(year, month - 1 + Number(horizonMonths) - 1, 1));
  return `${end.getUTCFullYear()}-${String(end.getUTCMonth() + 1).padStart(2, "0")}`;
}

export function validateGoalReverseSimulation(values, profile) {
  const errors = validateSimulationAssumptions(values);
  if (!positiveMoney(values.targetAmount)) {
    errors.targetAmount = "목표 순자산은 0원보다 큰 금액으로, 소수점 둘째 자리까지 입력해주세요.";
  }
  const targetEnd = goalTargetEndYearMonth(values.startYearMonth, values.horizonMonths);
  if (!targetEnd || targetEnd <= String(values.startYearMonth ?? "")) {
    errors.targetEndYearMonth = "목표 종료 연월은 시작 연월 이후이며 최대 60개월 이내여야 합니다.";
  }
  if (profileHasDebt(profile?.totalLoanBalance)
      && String(values.monthlyDebtPayment ?? "").trim() === "") {
    errors.monthlyDebtPayment = "부채가 있는 Profile은 월 대출상환액을 입력해야 합니다.";
  }
  return errors;
}

export function buildGoalReverseSimulationPayload(values) {
  return {
    goalType: "TARGET_NET_WORTH",
    targetAmount: String(values.targetAmount ?? "").trim(),
    ...buildSimulationRequestFields(values),
  };
}

export function goalRequestErrorMessage(error) {
  if (!(error instanceof ApiError)) return "목표 역산을 완료하지 못했습니다. 잠시 후 다시 시도해주세요.";
  if (error.status === 401 || error.status === 403) return "인증 세션이 만료되었습니다. 다시 로그인해주세요.";
  if (error.code === "VALIDATION_FAILED" || error.code === "INVALID_REQUEST") {
    return "입력한 목표와 계산 가정을 다시 확인해주세요.";
  }
  if (error.status === 404) return "최신 Financial Profile을 찾을 수 없습니다.";
  if (error.status >= 500 || error.status === 0) {
    return "목표 계산 서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
  }
  return "목표 역산 요청을 안전하게 처리하지 못했습니다. 다시 시도해주세요.";
}

export async function reverseSimulateGoal(values) {
  const text = await apiRequest("/api/goals/reverse-simulate", {
    method: "POST",
    body: buildGoalReverseSimulationPayload(values),
    responseType: "text",
  });
  return normalizeGoalReverseSimulation(text);
}
