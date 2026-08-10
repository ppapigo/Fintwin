import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCurrentProfile, getProfileHistory } from "../api/financialProfileApi";
import { ProfileSummaryPage } from "./ProfileSummaryPage";

vi.mock("../api/financialProfileApi", () => ({
  getCurrentProfile: vi.fn(),
  getProfileHistory: vi.fn(),
}));

const CURRENT = {
  version: 2,
  createdAt: "2026-08-10T10:00:00",
  monthlyIncome: "5000.00",
  cashAssets: "1000.00",
  deposits: "2000.00",
  investmentAssets: "3000.00",
  totalLoanBalance: "500.00",
  loanInterestRate: "3.1250",
  monthlyFixedExpenses: "100.00",
  monthlyVariableExpenses: "200.00",
  monthlySavings: "300.00",
  monthlyInvestments: "400.00",
};

describe("ProfileSummaryPage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(getProfileHistory).mockReset();
  });

  it("shows exact summary calculations and immutable version history without identifiers", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue(CURRENT);
    vi.mocked(getProfileHistory).mockResolvedValue([
      CURRENT,
      { ...CURRENT, version: 1, createdAt: "2026-08-01T10:00:00", monthlyIncome: "4500.00" },
    ]);

    render(<MemoryRouter><ProfileSummaryPage /></MemoryRouter>);

    expect(await screen.findByText("내 금융 프로필")).toBeInTheDocument();
    expect(screen.getByText("6,000원")).toBeInTheDocument();
    expect(screen.getAllByText("5,500원").length).toBeGreaterThan(0);
    expect(screen.getByText("Version 2")).toBeInTheDocument();
    expect(screen.getByText("Version 1")).toBeInTheDocument();
    expect(screen.getByText("다음 단계에서 설정")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("userId");
    expect(document.body.textContent).not.toContain("Profile ID");
  });
});
