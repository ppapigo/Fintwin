import { describe, expect, it } from "vitest";
import { addMoney, formatWon, subtractMoney } from "./money";

describe("exact money helpers", () => {
  it("calculates beyond JavaScript safe integers without Number precision loss", () => {
    expect(addMoney("99999999999999999.99", "0.01")).toBe("100000000000000000");
    expect(subtractMoney("100000000000000000.00", "0.01")).toBe("99999999999999999.99");
    expect(formatWon("99999999999999999.99")).toBe("99,999,999,999,999,999.99원");
    expect(formatWon("-0.50")).toBe("-0.50원");
  });
});
