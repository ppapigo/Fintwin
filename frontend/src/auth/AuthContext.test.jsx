import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { getCurrentAuth } from "../api/authApi";
import { AuthProvider } from "./AuthContext";
import { useAuth } from "./useAuth";

vi.mock("../api/authApi", () => ({
  getCurrentAuth: vi.fn(),
  logout: vi.fn(),
}));

function Probe() {
  const { status, provider } = useAuth();
  return <div>{status}:{provider || "none"}</div>;
}

describe("AuthProvider", () => {
  beforeEach(() => vi.mocked(getCurrentAuth).mockReset());

  it("resolves an authenticated session on application start", async () => {
    vi.mocked(getCurrentAuth).mockResolvedValue({ authenticated: true, provider: "google" });
    render(<AuthProvider><Probe /></AuthProvider>);
    expect(screen.getByText("loading:none")).toBeInTheDocument();
    expect(await screen.findByText("authenticated:google")).toBeInTheDocument();
  });

  it("distinguishes an anonymous session from a backend outage", async () => {
    vi.mocked(getCurrentAuth).mockResolvedValueOnce({ authenticated: false, provider: null });
    const first = render(<AuthProvider><Probe /></AuthProvider>);
    expect(await screen.findByText("anonymous:none")).toBeInTheDocument();
    first.unmount();

    vi.mocked(getCurrentAuth).mockRejectedValueOnce(new Error("offline"));
    render(<AuthProvider><Probe /></AuthProvider>);
    await waitFor(() => expect(screen.getByText("error:none")).toBeInTheDocument());
  });
});
