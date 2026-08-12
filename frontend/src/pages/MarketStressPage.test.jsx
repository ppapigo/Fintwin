import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import { getMarketContext, runMarketStress } from "../api/marketStressApi";
import { MarketStressPage } from "./MarketStressPage";

vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));
vi.mock("../api/marketStressApi", async (importOriginal) => {
  const original = await importOriginal();
  return { ...original, getMarketContext: vi.fn(), runMarketStress: vi.fn() };
});
vi.mock("../components/marketstress/MarketStressResults", () => ({
  MarketStressResults: () => <div>Market Stress 결과 표시</div>,
}));

const PROFILE = {
  version: 4,
  monthlyIncome: "4000000.00",
  investmentAssets: "10000000.00",
  totalLoanBalance: "12000000.00",
};

function renderPage() {
  return render(<MemoryRouter initialEntries={["/market-stress"]}><Routes>
    <Route path="/market-stress" element={<MarketStressPage />} />
    <Route path="/profile/setup" element={<h1>Profile 설정</h1>} />
    <Route path="/" element={<h1>로그인 화면</h1>} />
  </Routes></MemoryRouter>);
}

describe("MarketStressPage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(getMarketContext).mockReset();
    vi.mocked(runMarketStress).mockReset();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
    vi.mocked(getMarketContext).mockResolvedValue({ status: "UNAVAILABLE", observations: [] });
  });

  it("shows profile-first guidance without running a simulation", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue(null);
    renderPage();

    expect(await screen.findByText("Financial Profile이 먼저 필요합니다")).toBeInTheDocument();
    expect(runMarketStress).not.toHaveBeenCalled();
  });

  it("keeps the screen usable when official market data is unavailable", async () => {
    renderPage();

    expect(await screen.findByRole("heading", { name: /관측과 가정을 분리해/ })).toBeInTheDocument();
    expect(screen.getAllByText("UNAVAILABLE")).toHaveLength(3);
    expect(screen.getByText(/현재 관측값은 화면의 배경정보/)).toBeInTheDocument();
  });

  it("blocks duplicate submission while one deterministic request is in flight", async () => {
    let resolveRequest;
    vi.mocked(runMarketStress).mockImplementation(() => new Promise((resolve) => { resolveRequest = resolve; }));
    renderPage();
    const button = await screen.findByRole("button", { name: "Market Stress 실행" });
    const form = button.closest("form");

    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(runMarketStress).toHaveBeenCalledTimes(1);
    expect(await screen.findByRole("button", { name: "Stress 계산 중…" })).toBeDisabled();
    resolveRequest({});
    await waitFor(() => expect(screen.getByText("Market Stress 결과 표시")).toBeInTheDocument());
  });

  it("shows a safe validation message before calling the backend", async () => {
    renderPage();
    await screen.findByRole("button", { name: "Market Stress 실행" });
    fireEvent.change(screen.getByLabelText("국내 주식 Exposure"), { target: { value: "99999999" } });
    fireEvent.submit(screen.getByRole("button", { name: "Market Stress 실행" }).closest("form"));

    expect(await screen.findByText(/Exposure 합계는 현재 투자자산/)).toBeInTheDocument();
    expect(runMarketStress).not.toHaveBeenCalled();
  });

  it("handles an expired authentication session without exposing server details", async () => {
    vi.mocked(getCurrentProfile).mockRejectedValue(new ApiError("raw server text", { status: 401 }));
    renderPage();

    expect(await screen.findByText("인증 세션이 만료되었습니다")).toBeInTheDocument();
    expect(screen.queryByText("raw server text")).not.toBeInTheDocument();
  });
});
