import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import { compareMultipleScenarios } from "../api/scenarioLabApi";
import { ScenarioLabPage } from "./ScenarioLabPage";

vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));
vi.mock("../api/scenarioLabApi", async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, compareMultipleScenarios: vi.fn() };
});
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  LineChart: ({ children }) => <div>{children}</div>,
  CartesianGrid: () => null,
  Line: ({ name, dataKey }) => <span data-testid={`line-${dataKey}`}>{name}</span>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

const PROFILE = { version: 4, totalLoanBalance: "2000000.00" };
const TOTALS = { income: "36000000.00", consumption: "16800000.00", debtInterest: "100000.00", principalRepaid: "1900000.00", savingsAllocated: "4800000.00", investmentContributions: "2400000.00", investmentReturn: "0.00" };
const MONTH = { monthNumber: 1, yearMonth: "2026-08", netWorth: "12345678.90", liquidAssets: "6000000.00", investmentAssets: "8000000.00", totalFinancialAssets: "14000000.00", remainingDebt: "1654321.10", disposableCashFlow: "1300000.00", cumulativeTotals: TOTALS, cashShortfall: false, negativeAmortization: false };
const DELTA = { monthNumber: 12, yearMonth: "2027-07", netWorthDelta: "-1000000.00", liquidAssetsDelta: "-1000000.00", investmentAssetsDelta: "0.00", totalFinancialAssetsDelta: "-1000000.00", debtDelta: "0.00", cumulativeIncomeDelta: "0.00", cumulativeConsumptionDelta: "1000000.00", cumulativeDebtInterestDelta: "0.00", cumulativePrincipalRepaidDelta: "0.00", cumulativeInvestmentContributionDelta: "0.00", cumulativeInvestmentReturnDelta: "0.00" };
const BASELINE = { monthlyResults: [MONTH], checkpoints: [], finalCumulativeTotals: TOTALS, finalLiquidAssets: "6000000.00", finalInvestmentAssets: "8000000.00", finalTotalFinancialAssets: "14000000.00", finalDebt: "1654321.10", finalNetWorth: "12345678.90", lastMonthDisposableCashFlow: "1300000.00", cashShortfall: false, negativeAmortization: false };
const RESULT = {
  financialProfileVersion: 4, horizonMonths: 12, baseline: BASELINE,
  scenarios: [
    { ...BASELINE, scenarioKey: "B", label: "자동차 구매", finalLiquidAssets: "5000000.00", finalTotalFinancialAssets: "13000000.00", finalNetWorth: "11345678.90", lastMonthDisposableCashFlow: "300000.00", baselineDelta: DELTA, residualDelta: "0.00", normalizedEvents: [{ eventId: "event-1" }], warnings: [{ scope: "SCENARIO", scenarioKey: "B", code: "NET_WORTH_BELOW_BASELINE", affectedYearMonth: "2027-07" }] },
    { ...BASELINE, scenarioKey: "C", label: "소비 절감", finalNetWorth: "13345678.90", baselineDelta: { ...DELTA, netWorthDelta: "1000000.00" }, residualDelta: "0.00", normalizedEvents: [{ eventId: "event-2" }], warnings: [] },
    { ...BASELINE, scenarioKey: "D", label: "소득 중단", finalNetWorth: "345678.90", baselineDelta: { ...DELTA, netWorthDelta: "-12000000.00" }, residualDelta: "0.00", normalizedEvents: [{ eventId: "event-3" }], warnings: [{ scope: "SCENARIO", scenarioKey: "D", code: "CASH_SHORTFALL", affectedYearMonth: "2026-10" }] },
  ],
  checkpointComparisons: [{ monthNumber: 12, yearMonth: "2027-07", baseline: { ...MONTH, monthNumber: 12, yearMonth: "2027-07" }, scenarios: [{ scenarioKey: "B", label: "자동차 구매", result: { ...MONTH, monthNumber: 12, yearMonth: "2027-07", netWorth: "11345678.90" }, baselineDelta: DELTA }] }],
  calculationWarnings: [{ scope: "SCENARIO", scenarioKey: "B", code: "NET_WORTH_BELOW_BASELINE", affectedYearMonth: "2027-07" }, { scope: "SCENARIO", scenarioKey: "D", code: "CASH_SHORTFALL", affectedYearMonth: "2026-10" }],
  calculationBasis: { monthlyRateFormula: "annual / 100 / 12", moneyRounding: "2 decimals", savingsTreatment: "liquid", investmentTreatment: "transfer" },
  disclaimer: "Deterministic only.",
};

function renderPage() {
  return render(<MemoryRouter initialEntries={["/scenario-lab"]}><Routes>
    <Route path="/scenario-lab" element={<ScenarioLabPage />} />
    <Route path="/profile/setup" element={<h1>Profile 설정</h1>} />
    <Route path="/" element={<h1>로그인</h1>} />
  </Routes></MemoryRouter>);
}

async function fillFirstScenario() {
  await screen.findByRole("heading", { name: /네 가지 선택 비교/ });
  fireEvent.change(screen.getByLabelText("적용 월"), { target: { value: "2026-09" } });
  fireEvent.change(screen.getByLabelText("지출 금액"), { target: { value: "1000000" } });
  fireEvent.change(screen.getByLabelText("월 대출상환액"), { target: { name: "monthlyDebtPayment", value: "300000" } });
}

describe("ScenarioLabPage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(compareMultipleScenarios).mockReset();
  });

  it("shows the profile-first state and links to setup without simulation", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(null);
    renderPage();
    expect(await screen.findByRole("heading", { name: "Financial Profile이 먼저 필요합니다" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Profile 설정으로 이동" }));
    expect(screen.getByRole("heading", { name: "Profile 설정" })).toBeInTheDocument();
    expect(compareMultipleScenarios).not.toHaveBeenCalled();
  });

  it("adds, clones and deletes scenarios and enforces the four-scenario limit", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    renderPage();
    await screen.findByRole("heading", { name: /네 가지 선택 비교/ });
    await user.click(screen.getByRole("button", { name: "복제" }));
    expect(screen.getAllByText(/STRUCTURED SCENARIO/)).toHaveLength(2);
    await user.click(screen.getByRole("button", { name: /Scenario 추가 \(2\/4\)/ }));
    await user.click(screen.getByRole("button", { name: /Scenario 추가 \(3\/4\)/ }));
    expect(screen.getAllByText(/STRUCTURED SCENARIO/)).toHaveLength(4);
    expect(screen.getByRole("button", { name: /Scenario 추가 \(4\/4\)/ })).toBeDisabled();
    await user.click(screen.getAllByRole("button", { name: "삭제" })[0]);
    expect(screen.getAllByText(/STRUCTURED SCENARIO/)).toHaveLength(3);
  });

  it("blocks duplicate submit and sends only in-memory scenarios without internal identifiers", async () => {
    let complete;
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(compareMultipleScenarios).mockImplementation(() => new Promise((resolve) => { complete = resolve; }));
    renderPage();
    await fillFirstScenario();
    const submit = screen.getByRole("button", { name: "Scenario Lab 실행" });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(compareMultipleScenarios).toHaveBeenCalledOnce();
    const [values, scenarios] = vi.mocked(compareMultipleScenarios).mock.calls[0];
    expect(values.monthlyDebtPayment).toBe("300000");
    expect(scenarios[0].events[0].amount).toBe("1000000");
    expect(JSON.stringify([values, scenarios])).not.toMatch(/userId|profileId|oauth|sessionId/);
    expect(submit).toBeDisabled();
    await act(async () => complete(RESULT));
  });

  it("renders backend cards, table, checkpoints and warning mappings without recalculating values", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(compareMultipleScenarios).mockResolvedValue(RESULT);
    renderPage();
    await fillFirstScenario();
    fireEvent.click(screen.getByRole("button", { name: "Scenario Lab 실행" }));
    expect(await screen.findByRole("heading", { name: "같은 출발점, 다른 선택" })).toBeInTheDocument();
    expect(screen.getAllByText("자동차 구매").length).toBeGreaterThan(0);
    expect(screen.getAllByText("11,345,678.90원").length).toBeGreaterThan(0);
    expect(screen.getByText(/최종 순자산이 기준안보다 낮습니다/)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "1·3·5년 Checkpoint" })).toBeInTheDocument();
    expect(screen.getAllByText(/-1,000,000원/).length).toBeGreaterThan(0);
  });

  it("switches chart metrics and mobile scenario selection using only backend series", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(compareMultipleScenarios).mockResolvedValue(RESULT);
    renderPage();
    await fillFirstScenario();
    fireEvent.click(screen.getByRole("button", { name: "Scenario Lab 실행" }));
    await screen.findByRole("heading", { name: "월별 경로 비교" });
    await user.selectOptions(screen.getByLabelText("Chart Metric"), "investmentAssets");
    expect(screen.getByLabelText("Chart Metric")).toHaveValue("investmentAssets");
    await user.selectOptions(screen.getByLabelText("모바일 표시 Scenario"), "C");
    expect(screen.getByTestId("line-C")).toBeInTheDocument();
    expect(screen.queryByTestId("line-B")).not.toBeInTheDocument();
  });

  it("shows safe authentication expiry and never writes scenarios or results to browser storage", async () => {
    const localSpy = vi.spyOn(Storage.prototype, "setItem");
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(compareMultipleScenarios).mockRejectedValue(new ApiError("raw secret", { status: 401, code: "RAW" }));
    renderPage();
    await fillFirstScenario();
    fireEvent.click(screen.getByRole("button", { name: "Scenario Lab 실행" }));
    expect(await screen.findByText("인증 세션이 만료됐습니다")).toBeInTheDocument();
    expect(screen.queryByText("raw secret")).not.toBeInTheDocument();
    expect(localSpy).not.toHaveBeenCalled();
    localSpy.mockRestore();
  });
});
