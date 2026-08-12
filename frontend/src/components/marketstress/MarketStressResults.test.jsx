import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MarketStressResults } from "./MarketStressResults";

vi.mock("recharts", () => {
  const Container = ({ children }) => <div>{children}</div>;
  return {
    ResponsiveContainer: Container,
    LineChart: Container,
    CartesianGrid: () => null,
    Legend: () => null,
    Line: () => null,
    Tooltip: () => null,
    XAxis: () => null,
    YAxis: () => null,
  };
});

const MONTH = {
  yearMonth: "2026-08",
  netWorth: "10000000.00",
  investmentAssets: "5000000.00",
  liquidAssets: "7000000.00",
  remainingDebt: "2000000.00",
};

const RESULT = {
  financialProfileVersion: 4,
  horizonMonths: 12,
  baseline: { monthlyResults: [MONTH] },
  stressed: { monthlyResults: [{ ...MONTH, netWorth: "8765432.10" }] },
  marketImpactBreakdown: {
    shockYearMonth: "2026-10",
    domesticExposureAtShock: "3000000.00",
    domesticStockImpact: "-600000.00",
    overseasExposureAtShock: "2000000.00",
    overseasStockImpact: "-500000.00",
    exchangeRateImpact: "150000.00",
    totalInvestmentImpact: "-950000.00",
    additionalDebtInterest: "12345.67",
    finalNetWorthDelta: "-1234567.90",
  },
  riskComparison: {
    baseline: { cashShortfallMonthCount: 0, negativeAmortizationMonthCount: 0 },
    stressed: { cashShortfallMonthCount: 1, negativeAmortizationMonthCount: 0,
      minimumLiquidAssets: "-100.00", finalRemainingDebt: "2100000.00" },
  },
  goalMarginComparison: {
    status: "BASELINE_ONLY", targetNetWorth: "10000000.00",
    baselineMargin: "500000.00", stressedMargin: "-734567.90", marginDelta: "-1234567.90",
  },
  warnings: [{ code: "CASH_SHORTFALL" }, { code: "UNKNOWN_FUTURE_WARNING" }],
};

describe("MarketStressResults", () => {
  it("shows backend impact values and maps known and unknown warnings safely", () => {
    render(<MarketStressResults result={RESULT} />);

    expect(screen.getByText("-950,000원")).toBeInTheDocument();
    expect(screen.getByText("Stress 결과에 현금 부족 월이 포함됩니다.")).toBeInTheDocument();
    expect(screen.getByText(/추가 위험이 확인되었습니다/)).toBeInTheDocument();
  });

  it("switches chart metric without recalculating backend results", () => {
    render(<MarketStressResults result={RESULT} />);
    const select = screen.getByLabelText("지표");

    fireEvent.change(select, { target: { value: "investmentAssets" } });

    expect(select).toHaveValue("investmentAssets");
    expect(screen.getAllByText("-1,234,567.90원")).toHaveLength(2);
  });
});
