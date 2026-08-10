import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCurrentProfile } from "../api/financialProfileApi";
import { AuthContext } from "../auth/AuthContext";
import { AuthCallbackPage } from "./AuthCallbackPage";

vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));

function renderCallback(entry, status = "authenticated") {
  return render(
    <AuthContext.Provider value={{ status, provider: "google", refreshAuth: vi.fn(), logout: vi.fn() }}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/auth/callback" element={<AuthCallbackPage />} />
          <Route path="/profile/summary" element={<div>summary route</div>} />
          <Route path="/profile/setup" element={<div>setup route</div>} />
          <Route path="/" element={<div>landing route</div>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe("AuthCallbackPage", () => {
  beforeEach(() => vi.mocked(getCurrentProfile).mockReset());

  it("routes an authenticated user with a profile to the summary", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue({ version: 1 });
    renderCallback("/auth/callback?status=success&token=ignored&redirect=https://evil.example");
    expect(await screen.findByText("summary route")).toBeInTheDocument();
  });

  it("routes a user without a profile to onboarding", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue(null);
    renderCallback("/auth/callback?status=success");
    expect(await screen.findByText("setup route")).toBeInTheDocument();
  });

  it("shows a safe failure without querying financial data", () => {
    renderCallback("/auth/callback?status=failed&code=secret-internal-code");
    expect(screen.getByText("로그인을 완료하지 못했습니다")).toBeInTheDocument();
    expect(screen.queryByText("secret-internal-code")).not.toBeInTheDocument();
    expect(getCurrentProfile).not.toHaveBeenCalled();
  });
});
