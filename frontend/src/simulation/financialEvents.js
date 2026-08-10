const YEAR_MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const POSITIVE_MONEY_PATTERN = /^\d{1,17}(?:\.\d{1,2})?$/;
const SIGNED_MONEY_PATTERN = /^-?\d{1,17}(?:\.\d{1,2})?$/;
let fallbackSequence = 0;

export const EVENT_DEFINITIONS = Object.freeze({
  ONE_TIME_EXPENSE: { label: "일회성 지출", description: "특정 월에 한 번 발생하는 지출", timing: "single", valueField: "amount", valueLabel: "지출 금액", positive: true },
  RECURRING_EXPENSE_CHANGE: { label: "월 생활비 변경", description: "종료 월을 포함해 월 지출을 증감", timing: "period", valueField: "monthlyDelta", valueLabel: "월 지출 증감액", signed: true },
  INCOME_CHANGE: { label: "월 소득 변경", description: "종료 월을 포함해 월 소득을 증감", timing: "period", valueField: "monthlyDelta", valueLabel: "월 소득 증감액", signed: true },
  INCOME_PAUSE: { label: "소득 중단", description: "기간 동안 소득을 0원으로 적용", timing: "period" },
  INVESTMENT_CONTRIBUTION_CHANGE: { label: "월 투자액 변경", description: "계획 투자 납입액을 월별로 증감", timing: "period", valueField: "monthlyDelta", valueLabel: "월 투자 증감액", signed: true },
  EXTRA_DEBT_REPAYMENT: { label: "추가 대출상환", description: "특정 월에 가용 현금 범위에서 추가 상환", timing: "single", valueField: "amount", valueLabel: "추가 상환액", positive: true },
});

export const EVENT_TYPES = Object.freeze(Object.keys(EVENT_DEFINITIONS));

function eventId() {
  fallbackSequence += 1;
  const randomPart = globalThis.crypto?.randomUUID?.().replaceAll("-", "").slice(0, 12)
    ?? `local${fallbackSequence.toString(36)}`;
  return `event-${randomPart}`;
}

export function createFinancialEvent(type = "ONE_TIME_EXPENSE", existingId) {
  const definition = EVENT_DEFINITIONS[type] ?? EVENT_DEFINITIONS.ONE_TIME_EXPENSE;
  return {
    eventId: existingId ?? eventId(),
    eventType: type,
    description: definition.label,
    effectiveYearMonth: "",
    startYearMonth: "",
    endYearMonth: "",
    amount: "",
    monthlyDelta: "",
  };
}

export function simulationEndYearMonth(startYearMonth, horizonMonths) {
  if (!YEAR_MONTH_PATTERN.test(String(startYearMonth)) || ![12, 36, 60].includes(Number(horizonMonths))) return "";
  const [year, month] = startYearMonth.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1 + Number(horizonMonths) - 1, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

function isPositiveMoney(value) {
  const text = String(value ?? "").trim();
  if (!POSITIVE_MONEY_PATTERN.test(text)) return false;
  const [integer, fraction = ""] = text.split(".");
  return BigInt(integer) * 100n + BigInt((fraction + "00").slice(0, 2)) > 0n;
}

function fieldKey(event, field) {
  return `${event.eventId}.${field}`;
}

export function validateFinancialEvents(events, simulationValues) {
  const errors = {};
  if (!Array.isArray(events) || events.length === 0) return { events: "이벤트를 한 개 이상 추가해주세요." };
  if (events.length > 20) errors.events = "이벤트는 최대 20개까지 추가할 수 있습니다.";
  const ids = new Set();
  const rangeStart = String(simulationValues.startYearMonth ?? "");
  const rangeEnd = simulationEndYearMonth(rangeStart, simulationValues.horizonMonths);

  for (const event of events) {
    if (!event.eventId || event.eventId.length > 100 || ids.has(event.eventId)) errors[fieldKey(event, "eventId")] = "이벤트 식별자가 유효하지 않거나 중복됐습니다.";
    ids.add(event.eventId);
    const definition = EVENT_DEFINITIONS[event.eventType];
    if (!definition) {
      errors[fieldKey(event, "eventType")] = "지원하지 않는 이벤트입니다.";
      continue;
    }
    const description = String(event.description ?? "").trim();
    if (!description || description.length > 200) errors[fieldKey(event, "description")] = "설명은 1자 이상 200자 이하여야 합니다.";

    if (definition.timing === "single") {
      const effective = String(event.effectiveYearMonth ?? "");
      if (!YEAR_MONTH_PATTERN.test(effective)) errors[fieldKey(event, "effectiveYearMonth")] = "적용 월을 선택해주세요.";
      else if (!rangeEnd || effective < rangeStart || effective > rangeEnd) errors[fieldKey(event, "effectiveYearMonth")] = `적용 월은 ${rangeStart}부터 ${rangeEnd} 사이여야 합니다.`;
    } else {
      const start = String(event.startYearMonth ?? "");
      const end = String(event.endYearMonth ?? "");
      if (!YEAR_MONTH_PATTERN.test(start)) errors[fieldKey(event, "startYearMonth")] = "시작 월을 선택해주세요.";
      if (!YEAR_MONTH_PATTERN.test(end)) errors[fieldKey(event, "endYearMonth")] = "종료 월을 선택해주세요.";
      if (YEAR_MONTH_PATTERN.test(start) && YEAR_MONTH_PATTERN.test(end)) {
        if (start > end) errors[fieldKey(event, "endYearMonth")] = "종료 월은 시작 월보다 빠를 수 없습니다.";
        else if (!rangeEnd || start < rangeStart || end > rangeEnd) errors[fieldKey(event, "startYearMonth")] = `기간은 ${rangeStart}부터 ${rangeEnd} 안에 있어야 합니다.`;
      }
    }

    if (definition.positive && !isPositiveMoney(event.amount)) errors[fieldKey(event, "amount")] = "0원보다 큰 금액을 소수점 2자리까지 입력해주세요.";
    if (definition.signed && !SIGNED_MONEY_PATTERN.test(String(event.monthlyDelta ?? "").trim())) errors[fieldKey(event, "monthlyDelta")] = "증가액은 양수, 감소액은 음수로 소수점 2자리까지 입력해주세요.";
  }
  return errors;
}

export function buildFinancialEventPayload(event) {
  const definition = EVENT_DEFINITIONS[event.eventType];
  const payload = {
    eventId: event.eventId,
    eventType: event.eventType,
    description: String(event.description).trim(),
  };
  if (definition.timing === "single") payload.effectiveYearMonth = event.effectiveYearMonth;
  if (definition.timing === "period") {
    payload.startYearMonth = event.startYearMonth;
    payload.endYearMonth = event.endYearMonth;
  }
  if (definition.valueField === "amount") payload.amount = String(event.amount).trim();
  if (definition.valueField === "monthlyDelta") payload.monthlyDelta = String(event.monthlyDelta).trim();
  return payload;
}
