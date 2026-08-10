import { ApiError, apiRequest } from "./apiClient";

export const PROFILE_FIELDS = Object.freeze([
  "monthlyIncome",
  "cashAssets",
  "deposits",
  "investmentAssets",
  "totalLoanBalance",
  "loanInterestRate",
  "monthlyFixedExpenses",
  "monthlyVariableExpenses",
  "monthlySavings",
  "monthlyInvestments",
]);

const FINANCIAL_NUMBER_PATTERN = new RegExp(
  `(\\"(?:${PROFILE_FIELDS.join("|")})\\"\\s*:\\s*)(-?\\d+(?:\\.\\d+)?)`,
  "g",
);

function parseProfileJson(text) {
  const precisionSafeText = text.replace(FINANCIAL_NUMBER_PATTERN, '$1"$2"');
  return JSON.parse(precisionSafeText);
}

function normalizeProfile(payload) {
  const values = Object.fromEntries(
    PROFILE_FIELDS.map((field) => [field, payload?.[field] == null ? "" : String(payload[field])]),
  );
  return {
    ...values,
    version: Number.isInteger(payload?.version) ? payload.version : 0,
    createdAt: typeof payload?.createdAt === "string" ? payload.createdAt : "",
  };
}

export function toProfilePayload(values) {
  return Object.fromEntries(PROFILE_FIELDS.map((field) => [field, String(values[field] ?? "").trim()]));
}

async function requestProfile(path, options) {
  const text = await apiRequest(path, { ...options, responseType: "text" });
  return normalizeProfile(parseProfileJson(text));
}

export async function getCurrentProfile() {
  try {
    return await requestProfile("/api/financial-profiles/current");
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export function createProfile(values) {
  return requestProfile("/api/financial-profiles", {
    method: "POST",
    body: toProfilePayload(values),
  });
}

export function updateProfile(values) {
  return requestProfile("/api/financial-profiles/current", {
    method: "PUT",
    body: toProfilePayload(values),
  });
}

export async function getProfileHistory() {
  const text = await apiRequest("/api/financial-profiles/history", { responseType: "text" });
  const payload = parseProfileJson(text);
  return Array.isArray(payload) ? payload.map(normalizeProfile) : [];
}
