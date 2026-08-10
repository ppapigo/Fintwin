import { ApiError, apiRequest, apiUrl, clearCsrfToken } from "./apiClient";

const PROVIDERS = new Set(["google", "kakao"]);

export function getOAuthLoginUrl(provider) {
  if (!PROVIDERS.has(provider)) throw new Error("Unsupported OAuth provider.");
  return apiUrl(`/oauth2/authorization/${provider}`);
}

export function startOAuthLogin(provider, locationObject = window.location) {
  locationObject.assign(getOAuthLoginUrl(provider));
}

export async function getCurrentAuth() {
  try {
    const payload = await apiRequest("/api/auth/me");
    const normalizedProvider = typeof payload?.provider === "string" ? payload.provider.toLowerCase() : null;
    return {
      authenticated: payload?.authenticated === true,
      provider: PROVIDERS.has(normalizedProvider) ? normalizedProvider : null,
    };
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return { authenticated: false, provider: null };
    }
    throw error;
  }
}

export async function logout() {
  await apiRequest("/api/auth/logout", { method: "POST" });
  clearCsrfToken();
}
