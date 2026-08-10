import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AuthContext } from "./AuthContext";
import { ProtectedRoute } from "./ProtectedRoute";

function renderRoute(status, initialEntry = "/private") {
  const value = { status, provider: null, error: null, refreshAuth: vi.fn(), logout: vi.fn() };
  return render(
    <AuthContext.Provider value={value}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path="/" element={<div>landing</div>} />
          <Route element={<ProtectedRoute />}><Route path="/private" element={<div>private</div>} /></Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

describe("ProtectedRoute", () => {
  it("renders private content only for an authenticated session", () => {
    renderRoute("authenticated");
    expect(screen.getByText("private")).toBeInTheDocument();
  });

  it("redirects an anonymous visitor to the landing route", () => {
    renderRoute("anonymous");
    expect(screen.getByText("landing")).toBeInTheDocument();
  });
});
