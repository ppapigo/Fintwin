import { describe, expect, it } from "vitest";
import {
  EVENT_TYPES,
  buildFinancialEventPayload,
  createFinancialEvent,
  validateFinancialEvents,
} from "./financialEvents";

const SIMULATION = { startYearMonth: "2026-08", horizonMonths: 12 };

function validEvent(type, index = 0) {
  return {
    ...createFinancialEvent(type, `event-${type}-${index}`),
    description: `event ${index}`,
    effectiveYearMonth: "2026-09",
    startYearMonth: "2026-09",
    endYearMonth: "2027-01",
    amount: "1000000.00",
    monthlyDelta: "-100000.00",
  };
}

describe("financialEvents", () => {
  it("validates all six event contracts", () => {
    const events = EVENT_TYPES.map(validEvent);

    expect(validateFinancialEvents(events, SIMULATION)).toEqual({});
  });

  it("sends only fields used by each backend event contract", () => {
    const oneTime = buildFinancialEventPayload(validEvent("ONE_TIME_EXPENSE"));
    const pause = buildFinancialEventPayload(validEvent("INCOME_PAUSE"));
    const recurring = buildFinancialEventPayload(validEvent("RECURRING_EXPENSE_CHANGE"));

    expect(oneTime).toEqual(expect.objectContaining({ effectiveYearMonth: "2026-09", amount: "1000000.00" }));
    expect(oneTime).not.toHaveProperty("startYearMonth");
    expect(oneTime).not.toHaveProperty("monthlyDelta");
    expect(pause).toEqual(expect.objectContaining({ startYearMonth: "2026-09", endYearMonth: "2027-01" }));
    expect(pause).not.toHaveProperty("amount");
    expect(pause).not.toHaveProperty("monthlyDelta");
    expect(recurring).toHaveProperty("monthlyDelta", "-100000.00");
    expect(recurring).not.toHaveProperty("amount");
  });

  it("blocks duplicate identifiers, more than twenty events, and dates outside the simulation", () => {
    const first = validEvent("ONE_TIME_EXPENSE", 1);
    const duplicate = { ...validEvent("INCOME_PAUSE", 2), eventId: first.eventId };
    expect(validateFinancialEvents([first, duplicate], SIMULATION)).toHaveProperty(`${first.eventId}.eventId`);

    const tooMany = Array.from({ length: 21 }, (_, index) => validEvent("ONE_TIME_EXPENSE", index));
    expect(validateFinancialEvents(tooMany, SIMULATION)).toHaveProperty("events");

    const outside = { ...first, effectiveYearMonth: "2028-01" };
    expect(validateFinancialEvents([outside], SIMULATION)).toHaveProperty(`${outside.eventId}.effectiveYearMonth`);
  });
});
