const YEAR_MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const RATE_PATTERN = /^-?\d{1,3}(?:\.\d{1,6})?$/;
const MONEY_PATTERN = /^\d{1,17}(?:\.\d{1,2})?$/;
const HORIZONS = new Set([12, 36, 60]);

export const RATE_FIELDS = Object.freeze([
  ["annualIncomeGrowthRate", "연 소득 증가율", "매월 복리로 소득에 반영됩니다."],
  ["annualInflationRate", "연 물가상승률", "고정·변동지출에 매월 복리로 반영됩니다."],
  ["annualDepositInterestRate", "연 예금이율", "월초 유동자산에 적용됩니다."],
  ["annualInvestmentReturnRate", "연 투자수익률", "수익 또는 손실 가정이며 예측값이 아닙니다."],
]);

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export function createSimulationAssumptionValues() {
  return {
    startYearMonth: currentYearMonth(),
    horizonMonths: 60,
    annualIncomeGrowthRate: "0",
    annualInflationRate: "0",
    annualDepositInterestRate: "0",
    annualInvestmentReturnRate: "0",
    monthlyDebtPayment: "0",
  };
}

export function profileHasDebt(value) {
  return !/^0+(?:\.0+)?$/.test(String(value ?? "0"));
}

export function buildSimulationAssumptionsPayload(values) {
  return {
    annualIncomeGrowthRate: String(values.annualIncomeGrowthRate ?? "").trim(),
    annualInflationRate: String(values.annualInflationRate ?? "").trim(),
    annualDepositInterestRate: String(values.annualDepositInterestRate ?? "").trim(),
    annualInvestmentReturnRate: String(values.annualInvestmentReturnRate ?? "").trim(),
    monthlyDebtPayment: String(values.monthlyDebtPayment ?? "").trim(),
  };
}

export function buildSimulationRequestFields(values) {
  return {
    startYearMonth: String(values.startYearMonth ?? "").trim(),
    horizonMonths: Number(values.horizonMonths),
    assumptions: buildSimulationAssumptionsPayload(values),
  };
}

function validateRate(value, minimum) {
  const text = String(value ?? "").trim();
  if (!RATE_PATTERN.test(text)) return false;
  const number = Number(text);
  return Number.isFinite(number) && number >= minimum && number <= 100;
}

export function validateSimulationAssumptions(values) {
  const errors = {};
  if (!YEAR_MONTH_PATTERN.test(String(values.startYearMonth ?? ""))) errors.startYearMonth = "시작 연월을 선택해주세요.";
  if (!HORIZONS.has(Number(values.horizonMonths))) errors.horizonMonths = "기간은 12, 36, 60개월 중 하나여야 합니다.";
  if (!validateRate(values.annualIncomeGrowthRate, -100)) errors.annualIncomeGrowthRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  if (!validateRate(values.annualInflationRate, -100)) errors.annualInflationRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  if (!validateRate(values.annualDepositInterestRate, 0)) errors.annualDepositInterestRate = "0%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  if (!validateRate(values.annualInvestmentReturnRate, -100)) errors.annualInvestmentReturnRate = "-100%부터 100% 사이, 소수점 6자리까지 입력해주세요.";
  if (!MONEY_PATTERN.test(String(values.monthlyDebtPayment ?? "").trim())) errors.monthlyDebtPayment = "0원 이상, 소수점 2자리까지 입력해주세요.";
  return errors;
}
