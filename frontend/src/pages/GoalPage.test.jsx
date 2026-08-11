import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import { reverseSimulateGoal } from "../api/goalReverseSimulationApi";
import { GoalPage } from "./GoalPage";

vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));
vi.mock("../api/goalReverseSimulationApi", async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, reverseSimulateGoal: vi.fn() };
});
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  LineChart: ({ children }) => <div>{children}</div>,
  CartesianGrid: () => null,
  Legend: () => null,
  Line: ({ name }) => <span>{name}</span>,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

const PROFILE = { version: 2, monthlyIncome: "3000000.00", totalLoanBalance: "10000000.00" };
const MONTH = {
  monthNumber: 1,
  yearMonth: "2026-08",
  liquidAssets: "5000000.00",
  investmentAssets: "1000000.00",
  remainingDebt: "9000000.00",
  netWorth: "-3000000.00",
  cashShortfall: false,
  negativeAmortization: false,
};

function plan(planType, planStatus = "ACHIEVABLE", warning = null) {
  const labels = {
    REDUCE_EXPENSE: ["RECURRING_EXPENSE_CHANGE", "지출 절감", "-500000.00"],
    INCREASE_INCOME: ["INCOME_CHANGE", "소득 증가", "500000.00"],
    REDUCE_EXPENSE_AND_INVEST: ["INVESTMENT_CONTRIBUTION_CHANGE", "지출 절감 후 투자", "500000.00"],
  };
  const [eventType, description, monthlyDelta] = labels[planType];
  return {
    planType,
    planStatus,
    requiredMonthlyAmount: planStatus === "INFEASIBLE" ? null : "500000.00",
    maximumMonthlyAmountTested: "524288.00",
    generatedEvents: planStatus === "INFEASIBLE" ? [] : [{ eventType, startYearMonth: "2026-08", endYearMonth: "2031-07", effectiveYearMonth: null, amount: null, monthlyDelta, description }],
    projectedFinalNetWorth: planStatus === "INFEASIBLE" ? "41000000.00" : "50000003.00",
    goalMargin: planStatus === "INFEASIBLE" ? "-9000000.00" : "3.00",
    firstAchievedYearMonth: planStatus === "INFEASIBLE" ? null : "2031-07",
    achieved: planStatus !== "INFEASIBLE",
    solverIterations: 41,
    appliedConstraints: ["VARIABLE_EXPENSE_LIMIT"],
    warnings: warning ? [{ code: warning }] : [],
    projectedResult: { monthlyResults: [{ ...MONTH }, { ...MONTH, monthNumber: 60, yearMonth: "2031-07", liquidAssets: "49000000.00", investmentAssets: "10000000.00", remainingDebt: "8999997.00", netWorth: "50000003.00" }] },
  };
}

const RESULT = {
  financialProfileVersion: 2,
  goalType: "TARGET_NET_WORTH",
  targetAmount: "50000000.00",
  startYearMonth: "2026-08",
  targetEndYearMonth: "2031-07",
  horizonMonths: 60,
  assumptions: {},
  goalStatus: "ACHIEVABLE",
  currentNetWorth: "-3000000.00",
  baselineFinalNetWorth: "40000000.00",
  goalGap: "123.45",
  baselineFirstAchievedYearMonth: null,
  baseline: { monthlyResults: [{ ...MONTH }, { ...MONTH, monthNumber: 60, yearMonth: "2031-07", netWorth: "40000000.00" }] },
  plans: [plan("REDUCE_EXPENSE"), plan("INCREASE_INCOME"), plan("REDUCE_EXPENSE_AND_INVEST")],
  solverMetadata: { searchResolution: "1.00", maximumIterationsPerPlan: 128, incomeSearchUpperLimit: "99999999999999999.99", totalIterations: 123, searchAlgorithm: "BOUNDED_BINARY_SEARCH", monotonicityBasis: "FINAL_NET_WORTH" },
  warnings: [],
  disclaimer: "Deterministic simulation only.",
};

function renderPage() {
  return render(<MemoryRouter initialEntries={["/goal"]}><Routes>
    <Route path="/goal" element={<GoalPage />} />
    <Route path="/profile/setup" element={<h1>Profile 설정</h1>} />
    <Route path="/" element={<h1>로그인</h1>} />
  </Routes></MemoryRouter>);
}

async function fillValidForm(user) {
  await screen.findByRole("heading", { name: /목표에서 현재로/ });
  await user.type(screen.getByLabelText("목표 순자산"), "50000000");
  fireEvent.change(screen.getByLabelText("시작 연월"), { target: { name: "startYearMonth", value: "2026-08" } });
  const repayment = screen.getByLabelText("월 대출상환액");
  await user.clear(repayment);
  await user.type(repayment, "300000");
}

describe("GoalPage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(reverseSimulateGoal).mockReset();
  });

  it("shows a profile-first state and lets the user move to setup", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(null);
    renderPage();
    expect(await screen.findByRole("heading", { name: "Financial Profile이 먼저 필요합니다" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Profile 설정으로 이동" }));
    expect(screen.getByRole("heading", { name: "Profile 설정" })).toBeInTheDocument();
    expect(reverseSimulateGoal).not.toHaveBeenCalled();
  });

  it("blocks duplicate submission and renders all three backend plans without recalculating values", async () => {
    const user = userEvent.setup();
    let complete;
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(reverseSimulateGoal).mockImplementation(() => new Promise((resolve) => { complete = resolve; }));
    renderPage();
    await fillValidForm(user);
    const submit = screen.getByRole("button", { name: "목표 달성 대안 계산" });
    await user.click(submit);
    expect(reverseSimulateGoal).toHaveBeenCalledOnce();
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(reverseSimulateGoal).toHaveBeenCalledOnce();

    await act(async () => complete(RESULT));
    expect(await screen.findByRole("heading", { name: "Backend가 계산한 대안" })).toBeInTheDocument();
    expect(screen.getAllByText("지출 절감").length).toBeGreaterThan(0);
    expect(screen.getAllByText("소득 증가").length).toBeGreaterThan(0);
    expect(screen.getAllByText("지출 절감 후 투자").length).toBeGreaterThan(0);
    expect(screen.getByText("123.45원")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/userId|profileId|financialProfileId/);
  });

  it("renders already achievable and infeasible states with mapped and unknown warnings", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(reverseSimulateGoal).mockResolvedValue({
      ...RESULT,
      goalStatus: "ALREADY_ACHIEVABLE",
      baselineFirstAchievedYearMonth: "2028-01",
      plans: [plan("REDUCE_EXPENSE", "INFEASIBLE", "EXPENSE_REDUCTION_INFEASIBLE"), plan("INCREASE_INCOME"), plan("REDUCE_EXPENSE_AND_INVEST")],
      warnings: [{ code: "ALREADY_ACHIEVABLE" }, { code: "NEW_WARNING_CODE" }],
    });
    renderPage();
    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "목표 달성 대안 계산" }));

    expect(await screen.findByText("추가 월 행동 없이 달성 가능한 목표입니다.")).toBeInTheDocument();
    expect(screen.getByText("현재 가정의 기준안만으로 목표 시점에 목표를 달성할 수 있습니다.")).toBeInTheDocument();
    expect(screen.getByText("NEW_WARNING_CODE")).toBeInTheDocument();
    expect(screen.getByText(/계산 과정에서 확인이 필요한 조건/)).toBeInTheDocument();
    expect(screen.getAllByText("검색 범위 내 불가능").length).toBeGreaterThan(0);
    expect(screen.getByText(/현재 변동지출 범위에서는/)).toBeInTheDocument();
  });

  it("keeps the backend all-infeasible state without inventing a fallback plan", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(reverseSimulateGoal).mockResolvedValue({
      ...RESULT,
      goalStatus: "NOT_ACHIEVABLE",
      plans: [
        plan("REDUCE_EXPENSE", "INFEASIBLE", "EXPENSE_REDUCTION_INFEASIBLE"),
        plan("INCREASE_INCOME", "INFEASIBLE", "SEARCH_LIMIT_REACHED"),
        plan("REDUCE_EXPENSE_AND_INVEST", "INFEASIBLE", "CASH_SHORTFALL"),
      ],
    });
    renderPage();
    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "목표 달성 대안 계산" }));

    expect(await screen.findByRole("heading", { name: "검색 범위 내 달성 불가" })).toBeInTheDocument();
    expect(document.querySelectorAll(".goal-plan-card--infeasible")).toHaveLength(3);
    expect(document.querySelectorAll(".goal-plan-card")).toHaveLength(3);
  });

  it("switches the chart metric without deriving new financial values", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(reverseSimulateGoal).mockResolvedValue(RESULT);
    renderPage();
    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "목표 달성 대안 계산" }));
    expect(await screen.findByRole("img", { name: /월별 순자산 비교/ })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Chart 지표"), "liquidAssets");
    expect(screen.getByRole("img", { name: /월별 유동자산 비교/ })).toBeInTheDocument();
  });

  it("shows safe validation and server errors without exposing raw details", async () => {
    const user = userEvent.setup();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(reverseSimulateGoal).mockRejectedValue(new ApiError("STACK TRACE: secret", { status: 500, code: "INTERNAL" }));
    renderPage();
    await screen.findByRole("heading", { name: /목표에서 현재로/ });
    await user.type(screen.getByLabelText("목표 순자산"), "1e9");
    await user.click(screen.getByRole("button", { name: "목표 달성 대안 계산" }));
    expect(screen.getByText(/목표 순자산은 0원보다 큰/)).toBeInTheDocument();
    expect(reverseSimulateGoal).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText("목표 순자산"));
    await user.type(screen.getByLabelText("목표 순자산"), "50000000");
    const repayment = screen.getByLabelText("월 대출상환액");
    await user.clear(repayment);
    await user.type(repayment, "300000");
    await user.click(screen.getByRole("button", { name: "목표 달성 대안 계산" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("목표 계산 서버에 일시적인 문제가 발생했습니다");
    expect(document.body).not.toHaveTextContent("STACK TRACE");
  });

  it("handles an expired session without showing the server error body", async () => {
    vi.mocked(getCurrentProfile).mockRejectedValue(new ApiError("OAuth subject secret", { status: 401 }));
    renderPage();
    expect(await screen.findByRole("heading", { name: "인증 세션이 만료되었습니다" })).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent("OAuth subject secret");
  });
});
