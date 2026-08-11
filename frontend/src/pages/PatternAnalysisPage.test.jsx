import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile, updateProfile } from "../api/financialProfileApi";
import { analyzePatternFile, downloadXlsxTemplate } from "../api/patternAnalysisApi";
import { PatternAnalysisPage } from "./PatternAnalysisPage";

vi.mock("../api/financialProfileApi", async (importOriginal) => ({
  ...(await importOriginal()),
  getCurrentProfile: vi.fn(),
  updateProfile: vi.fn(),
}));

vi.mock("../api/patternAnalysisApi", async (importOriginal) => ({
  ...(await importOriginal()),
  analyzePatternFile: vi.fn(),
  downloadXlsxTemplate: vi.fn(),
}));

const PROFILE = Object.freeze({
  monthlyIncome: "3000.00",
  cashAssets: "10000.00",
  deposits: "20000.00",
  investmentAssets: "15000.00",
  totalLoanBalance: "5000.00",
  loanInterestRate: "4.0000",
  monthlyFixedExpenses: "1000.00",
  monthlyVariableExpenses: "500.00",
  monthlySavings: "300.00",
  monthlyInvestments: "200.00",
  version: 3,
  createdAt: "2026-08-01T00:00:00Z",
});

const RESULT = Object.freeze({
  algorithmVersion: "fintwin-pattern-v1",
  analysisPeriod: { startYearMonth: "2026-01", endYearMonth: "2026-06", includedMonthCount: 6 },
  transactionCount: 24,
  monthlyCashFlows: [{
    yearMonth: "2026-01", transactionCount: 4, income: "3200.00", expenses: "1400.00",
    savingTransfers: "400.00", investmentTransfers: "250.00", debtPayments: "0.00",
    transfers: "0.00", monthlySurplus: "1800.00", liquidityAfterAllocations: "1150.00",
  }],
  averages: {
    monthlyIncome: "3200.00", monthlyExpenses: "1400.00", monthlySurplus: "1800.00",
    savingsRatePercent: "20.31",
  },
  categorySpending: [{ category: "HOUSING", totalExpenses: "6000.00", spendingRatioPercent: "71.43" }],
  recurringTransactions: [{
    type: "EXPENSE", category: "HOUSING", detectedMonthCount: 6,
    averageMonthlyAmount: "1000.00",
  }],
  profileDraft: {
    estimatedValues: {
      monthlyIncome: "3200.00", monthlyFixedExpenses: "1100.00", monthlyVariableExpenses: "300.00",
      monthlySavings: "400.00", monthlyInvestment: "250.00",
    },
  },
  currentProfileComparison: {
    financialProfileVersion: 3,
    currentValues: {
      monthlyIncome: "3000.00", monthlyFixedExpenses: "1000.00", monthlyVariableExpenses: "500.00",
      monthlySavings: "300.00", monthlyInvestment: "200.00",
    },
    draftValues: {
      monthlyIncome: "3200.00", monthlyFixedExpenses: "1100.00", monthlyVariableExpenses: "300.00",
      monthlySavings: "400.00", monthlyInvestment: "250.00",
    },
    deltas: {
      monthlyIncome: "200.00", monthlyFixedExpenses: "100.00", monthlyVariableExpenses: "-200.00",
      monthlySavings: "100.00", monthlyInvestment: "50.00",
    },
  },
  warnings: [{ code: "HIGH_EXPENSE_VOLATILITY" }, { code: "UNKNOWN_SAFE_WARNING" }],
});

function renderPage() {
  return render(<MemoryRouter><PatternAnalysisPage /></MemoryRouter>);
}

async function uploadAndAnalyze(user) {
  const input = document.querySelector("#pattern-file");
  const file = new File(["synthetic workbook"], "transactions.xlsx", {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  await user.upload(input, file);
  await user.click(screen.getByRole("button", { name: "Pattern 분석 실행" }));
}

describe("PatternAnalysisPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getCurrentProfile.mockResolvedValue(PROFILE);
    analyzePatternFile.mockResolvedValue(RESULT);
    updateProfile.mockResolvedValue({ ...PROFILE, monthlyIncome: "3200.00", version: 4 });
    downloadXlsxTemplate.mockResolvedValue(new Blob(["xlsx"]));
  });

  it("guides a user without a Profile to setup without exposing identifiers", async () => {
    getCurrentProfile.mockResolvedValue(null);
    renderPage();

    expect(await screen.findByText(/현재 Profile이 없습니다/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Profile 만들기" })).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/userId|profileId|financialProfileId/i);
  });

  it("uploads once, does not display the filename, and renders backend values without recalculation", async () => {
    const user = userEvent.setup();
    const storageSpy = vi.spyOn(Storage.prototype, "setItem");
    renderPage();
    await screen.findByText("현재 Profile v3");

    await uploadAndAnalyze(user);

    expect(analyzePatternFile).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("거래 패턴 분석 결과")).toBeInTheDocument();
    expect(screen.getAllByText("3,200원").length).toBeGreaterThan(0);
    expect(screen.getByText("-200원")).toBeInTheDocument();
    expect(screen.queryByText("transactions.xlsx")).not.toBeInTheDocument();
    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });

  it("blocks duplicate submissions while the same upload is running", async () => {
    let resolveAnalysis;
    analyzePatternFile.mockReturnValue(new Promise((resolve) => { resolveAnalysis = resolve; }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("현재 Profile v3");
    const input = document.querySelector("#pattern-file");
    await user.upload(input, new File(["xlsx"], "transactions.xlsx"));
    const form = screen.getByRole("button", { name: "Pattern 분석 실행" }).closest("form");

    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(analyzePatternFile).toHaveBeenCalledTimes(1);
    resolveAnalysis(RESULT);
    expect(await screen.findByText("거래 패턴 분석 결과")).toBeInTheDocument();
  });

  it("maps known and unknown warnings to safe user-facing text", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("현재 Profile v3");
    await uploadAndAnalyze(user);

    expect(await screen.findByText("월별 지출 변동성이 높습니다.")).toBeInTheDocument();
    expect(screen.getByText("분석 과정에서 검토가 필요한 항목이 발견되었습니다.")).toBeInTheDocument();
  });

  it("copies only selected draft fields and saves through the existing Profile update API", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("현재 Profile v3");
    await uploadAndAnalyze(user);
    await screen.findByText("거래 패턴 분석 결과");

    await user.click(screen.getByRole("checkbox", { name: "월 소득 분석값 반영" }));
    await user.click(screen.getByRole("button", { name: "선택값 수정 폼에서 검토" }));

    expect(document.querySelector("#monthlyIncome")).toHaveValue("3200.00");
    expect(document.querySelector("#monthlyFixedExpenses")).toHaveValue("1000.00");
    await user.click(screen.getByRole("button", { name: "검토 완료 후 새 Snapshot 저장" }));

    await waitFor(() => expect(updateProfile).toHaveBeenCalledTimes(1));
    expect(updateProfile.mock.calls[0][0]).toMatchObject({
      monthlyIncome: "3200.00",
      monthlyFixedExpenses: "1000.00",
      monthlyInvestments: "200.00",
    });
    expect(await screen.findByText(/새 Snapshot v4/)).toBeInTheDocument();
  });

  it("shows a safe authentication-expiry message without server details", async () => {
    analyzePatternFile.mockRejectedValue(new ApiError("raw parser stack trace", {
      status: 403,
      code: "ACCESS_DENIED",
    }));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("현재 Profile v3");
    await uploadAndAnalyze(user);

    expect(await screen.findByText(/인증 또는 보안 세션이 만료/)).toBeInTheDocument();
    expect(screen.queryByText(/raw parser stack trace/)).not.toBeInTheDocument();
  });
});
