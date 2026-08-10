import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { startOAuthLogin } from "../api/authApi";
import { AuthContext } from "../auth/AuthContext";
import { LandingPage } from "./LandingPage";

vi.mock("../api/authApi", () => ({ startOAuthLogin: vi.fn() }));
vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));

describe("LandingPage OAuth actions", () => {
  it("starts Google and Kakao with a browser-navigation command", async () => {
    const user = userEvent.setup();
    render(
      <AuthContext.Provider value={{ status: "anonymous", provider: null, refreshAuth: vi.fn(), logout: vi.fn() }}>
        <MemoryRouter><LandingPage /></MemoryRouter>
      </AuthContext.Provider>,
    );

    await user.click(screen.getByRole("button", { name: /Google로 시작하기/ }));
    await user.click(screen.getByRole("button", { name: /Kakao로 시작하기/ }));
    expect(startOAuthLogin).toHaveBeenNthCalledWith(1, "google");
    expect(startOAuthLogin).toHaveBeenNthCalledWith(2, "kakao");
  });
});
