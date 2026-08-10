import { describe, expect, it, vi } from "vitest";
import { getCurrentAuth, getOAuthLoginUrl, startOAuthLogin } from "./authApi";

function jsonResponse(body) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

describe("authApi", () => {
  it("normalizes the backend OAuthProvider enum without exposing identity data", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse({ authenticated: true, provider: "KAKAO" })));

    await expect(getCurrentAuth()).resolves.toEqual({ authenticated: true, provider: "kakao" });
  });

  it("uses fixed allowlisted browser redirect URLs for OAuth start", () => {
    const locationObject = { assign: vi.fn() };
    startOAuthLogin("google", locationObject);

    expect(getOAuthLoginUrl("kakao")).toBe("/oauth2/authorization/kakao");
    expect(locationObject.assign).toHaveBeenCalledWith("/oauth2/authorization/google");
    expect(() => getOAuthLoginUrl("unknown")).toThrow("Unsupported OAuth provider.");
  });
});
