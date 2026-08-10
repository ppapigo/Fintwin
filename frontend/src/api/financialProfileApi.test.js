import { describe, expect, it, vi } from "vitest";
import { createProfile, getCurrentProfile, PROFILE_FIELDS } from "./financialProfileApi";

const PROFILE_JSON = `{
  "id": 88,
  "userId": 42,
  "version": 3,
  "previousProfileId": 71,
  "monthlyIncome": 99999999999999999.99,
  "cashAssets": 100.10,
  "deposits": 200.20,
  "investmentAssets": 300.30,
  "totalLoanBalance": 40.00,
  "loanInterestRate": 3.1250,
  "monthlyFixedExpenses": 10.00,
  "monthlyVariableExpenses": 20.00,
  "monthlySavings": 30.00,
  "monthlyInvestments": 40.00,
  "createdAt": "2026-08-10T10:00:00"
}`;

function response(text) {
  return new Response(text, { status: 200, headers: { "Content-Type": "application/json" } });
}

describe("financialProfileApi", () => {
  it("preserves BigDecimal text precision and drops internal identifiers", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response(PROFILE_JSON)));

    const profile = await getCurrentProfile();

    expect(profile.monthlyIncome).toBe("99999999999999999.99");
    expect(profile.cashAssets).toBe("100.10");
    expect(profile).not.toHaveProperty("id");
    expect(profile).not.toHaveProperty("userId");
    expect(profile).not.toHaveProperty("previousProfileId");
  });

  it("sends only the ten contracted fields when creating a profile", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(JSON.stringify({ headerName: "X-XSRF-TOKEN", parameterName: "_csrf", token: "csrf" })))
      .mockResolvedValueOnce(response(PROFILE_JSON));
    vi.stubGlobal("fetch", fetchMock);
    const values = Object.fromEntries(PROFILE_FIELDS.map((field) => [field, "0"]));
    values.financialGoals = "not-in-contract";

    await createProfile(values);

    const body = JSON.parse(fetchMock.mock.calls[1][1].body);
    expect(Object.keys(body)).toEqual(PROFILE_FIELDS);
    expect(body).not.toHaveProperty("financialGoals");
    expect(body).not.toHaveProperty("userId");
  });
});
