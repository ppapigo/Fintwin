import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest, clearCsrfToken } from "./apiClient";

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("apiRequest", () => {
  beforeEach(() => clearCsrfToken());

  it("sends cookies on a GET without adding CSRF", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ authenticated: true, provider: "google" }));
    vi.stubGlobal("fetch", fetchMock);

    await apiRequest("/api/auth/me");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, options] = fetchMock.mock.calls[0];
    expect(options.credentials).toBe("include");
    expect(options.method).toBe("GET");
    expect(options.headers.get("X-XSRF-TOKEN")).toBeNull();
  });

  it("fetches CSRF in memory and adds it to a mutating request", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: "X-XSRF-TOKEN", parameterName: "_csrf", token: "memory-only" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);

    await apiRequest("/api/example", { method: "POST", body: { value: "10.00" } });

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/csrf");
    const [, mutation] = fetchMock.mock.calls[1];
    expect(mutation.credentials).toBe("include");
    expect(mutation.headers.get("X-XSRF-TOKEN")).toBe("memory-only");
    expect(mutation.body).toBe('{"value":"10.00"}');
  });

  it("sends FormData unchanged and lets the browser create the multipart boundary", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: "X-XSRF-TOKEN", parameterName: "_csrf", token: "memory-only" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal("fetch", fetchMock);
    const body = new FormData();
    body.append("file", new File(["synthetic"], "synthetic.xlsx"));

    await apiRequest("/api/patterns/analyze-xlsx", { method: "POST", body });

    const [, mutation] = fetchMock.mock.calls[1];
    expect(mutation.body).toBe(body);
    expect(mutation.headers.get("Content-Type")).toBeNull();
    expect(mutation.headers.get("X-XSRF-TOKEN")).toBe("memory-only");
  });

  it("does not automatically retry a failed mutation and refreshes only on the next user request", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ headerName: "X-XSRF-TOKEN", parameterName: "_csrf", token: "expired" }))
      .mockResolvedValueOnce(jsonResponse({ code: "ACCESS_DENIED", message: "raw security detail" }, 403));
    vi.stubGlobal("fetch", fetchMock);

    await expect(apiRequest("/api/example", { method: "PUT", body: {} })).rejects.toMatchObject({
      status: 403,
      message: "인증 세션을 확인한 뒤 다시 시도해주세요.",
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);

    fetchMock
      .mockResolvedValueOnce(jsonResponse({ headerName: "X-XSRF-TOKEN", parameterName: "_csrf", token: "fresh" }))
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    await apiRequest("/api/example", { method: "PUT", body: {} });
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("maps validation details without exposing the raw top-level server message", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({
      code: "VALIDATION_FAILED",
      message: "internal raw detail",
      fieldErrors: [{ field: "monthlyIncome", message: "0 이상이어야 합니다." }],
    }, 400)));

    await expect(apiRequest("/api/example")).rejects.toMatchObject({
      message: "입력값을 다시 확인해주세요.",
      fieldErrors: [{ field: "monthlyIncome", message: "0 이상이어야 합니다." }],
    });
  });
});
