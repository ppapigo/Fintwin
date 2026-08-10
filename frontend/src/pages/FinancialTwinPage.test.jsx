import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { runBaselineSimulation } from "../api/baselineSimulationApi";
import { getCurrentProfile } from "../api/financialProfileApi";
import { FinancialTwinPage } from "./FinancialTwinPage";

vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));
vi.mock("../api/baselineSimulationApi", async (importOriginal) => {
  const actual = await importOriginal();
  return { ...actual, runBaselineSimulation: vi.fn() };
});
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  LineChart: ({ children }) => <div>{children}</div>,
  CartesianGrid: () => null,
  Legend: () => null,
  Line: () => null,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

const PROFILE = {
  version: 2,
  monthlyIncome: "3000000.00",
  totalLoanBalance: "10000000.00",
};

const TOTALS = {
  income: "3000000.00",
  consumption: "1500000.00",
  debtInterest: "37500.00",
  principalRepaid: "262500.00",
  savingsAllocated: "300000.00",
  investmentContributions: "200000.00",
  investmentReturn: "0.00",
};

const MONTH = {
  monthNumber: 1,
  yearMonth: "2026-08",
  income: "3000000.00",
  fixedExpenses: "800000.00",
  variableExpenses: "700000.00",
  oneTimeExpense: "0.00",
  debtInterest: "37500.00",
  debtPayment: "300000.00",
  principalRepaid: "262500.00",
  savingsAllocation: "300000.00",
  investmentContribution: "200000.00",
  disposableCashFlow: "700000.00",
  totalFinancialAssets: "6200000.00",
  remainingDebt: "9737500.00",
  netWorth: "-3537500.00",
  cashShortfall: false,
  negativeAmortization: false,
};

const RESULT = {
  financialProfileVersion: 2,
  startYearMonth: "2026-08",
  horizonMonths: 60,
  assumptions: {
    annualIncomeGrowthRate: "0",
    annualInflationRate: "0",
    annualDepositInterestRate: "0",
    annualInvestmentReturnRate: "0",
    monthlyDebtPayment: "300000",
  },
  monthlyResults: [MONTH],
  checkpoints: [12, 36, 60].map((monthNumber) => ({
    monthNumber,
    yearMonth: "2031-07",
    totalFinancialAssets: "6200000.00",
    remainingDebt: "9737500.00",
    netWorth: "-3537500.00",
  })),
  finalCumulativeTotals: TOTALS,
  calculationBasis: {
    monthlyRateFormula: "annual percentage / 100 / 12",
    moneyRounding: "2 decimals, HALF_UP",
    savingsTreatment: "Savings stay within liquid assets.",
    investmentTreatment: "Contributions transfer between assets.",
    disclaimer: "Not a forecast or guarantee.",
  },
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/twin"]}>
      <Routes>
        <Route path="/twin" element={<FinancialTwinPage />} />
        <Route path="/profile/setup" element={<h1>프로필 설정</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("FinancialTwinPage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(runBaselineSimulation).mockReset();
  });

  it("redirects to profile setup when the authenticated user has no profile", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue(null);

    renderPage();

    expect(await screen.findByRole("heading", { name: "프로필 설정" })).toBeInTheDocument();
    expect(runBaselineSimulation).not.toHaveBeenCalled();
  });

  it("runs one 60-month request, disables duplicate submission, and renders baseline sections", async () => {
    const user = userEvent.setup();
    let completeRequest;
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(runBaselineSimulation).mockImplementation(() => new Promise((resolve) => { completeRequest = resolve; }));
    renderPage();

    expect(await screen.findByRole("heading", { name: /숫자로 먼저 보는/ })).toBeInTheDocument();
    const repayment = screen.getByLabelText("월 대출상환액");
    await user.clear(repayment);
    await user.type(repayment, "300000");
    const runButton = screen.getByRole("button", { name: "60개월 Baseline 실행" });
    await user.click(runButton);

    expect(runBaselineSimulation).toHaveBeenCalledOnce();
    expect(runButton).toBeDisabled();
    await user.click(runButton);
    expect(runBaselineSimulation).toHaveBeenCalledOnce();

    await act(async () => completeRequest(RESULT));

    expect(await screen.findByRole("heading", { name: "60개월 기준선이 완성되었습니다" })).toBeInTheDocument();
    expect(screen.getByText("1년 후")).toBeInTheDocument();
    expect(screen.getByText("3년 후")).toBeInTheDocument();
    expect(screen.getByText("5년 후")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "월별 현금흐름" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "누적 결과" })).toBeInTheDocument();
    expect(screen.getByText("기준 경고 없음")).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/financialProfileId|userId/);
  });
});
