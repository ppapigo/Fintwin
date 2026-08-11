const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);
const ALLOWED_CSRF_HEADERS = new Set(["X-XSRF-TOKEN", "X-CSRF-TOKEN"]);

let csrfState = null;

export class ApiError extends Error {
  constructor(message, { status = 0, code = "REQUEST_FAILED", fieldErrors = [] } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  get isCsrfFailure() {
    return this.status === 403 && MUTATING_METHODS.has(this.method);
  }
}

export function apiUrl(path) {
  if (!path.startsWith("/")) {
    throw new Error("API path must start with '/'.");
  }
  return `${API_BASE_URL}${path}`;
}

function safeErrorMessage(status, code) {
  if (code === "VALIDATION_FAILED") return "입력값을 다시 확인해주세요.";
  if (code === "RESOURCE_NOT_FOUND") return "요청한 정보를 찾을 수 없습니다.";
  if (code === "RESOURCE_CONFLICT") return "최신 정보와 충돌했습니다. 다시 불러온 뒤 시도해주세요.";
  if (status === 401 || status === 403) return "인증 세션을 확인한 뒤 다시 시도해주세요.";
  if (status >= 500) return "서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
  if (status === 0) return "서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.";
  return "요청을 처리하지 못했습니다. 다시 시도해주세요.";
}

function sanitizeFieldErrors(payload) {
  if (!Array.isArray(payload?.fieldErrors)) return [];
  return payload.fieldErrors
    .filter((item) => typeof item?.field === "string" && typeof item?.message === "string")
    .map(({ field, message }) => ({ field, message }));
}

async function parseJsonSafely(response) {
  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) return null;
  try {
    return await response.json();
  } catch {
    return null;
  }
}

async function createApiError(response, method) {
  const payload = await parseJsonSafely(response);
  const code = typeof payload?.code === "string" ? payload.code : "REQUEST_FAILED";
  const error = new ApiError(safeErrorMessage(response.status, code), {
    status: response.status,
    code,
    fieldErrors: sanitizeFieldErrors(payload),
  });
  error.method = method;
  return error;
}

export function clearCsrfToken() {
  csrfState = null;
}

async function ensureCsrfToken() {
  if (csrfState) return csrfState;

  let response;
  try {
    response = await fetch(apiUrl("/api/auth/csrf"), {
      method: "GET",
      credentials: "include",
      headers: { Accept: "application/json" },
    });
  } catch {
    throw new ApiError(safeErrorMessage(0), { status: 0 });
  }

  if (!response.ok) throw await createApiError(response, "GET");
  const payload = await parseJsonSafely(response);
  if (
    !payload ||
    typeof payload.token !== "string" ||
    !ALLOWED_CSRF_HEADERS.has(payload.headerName)
  ) {
    throw new ApiError("보안 토큰 응답을 확인할 수 없습니다.", {
      status: response.status,
      code: "INVALID_CSRF_RESPONSE",
    });
  }

  csrfState = { headerName: payload.headerName, token: payload.token };
  return csrfState;
}

export async function apiRequest(
  path,
  { method = "GET", body, headers = {}, responseType = "json", signal } = {},
) {
  const normalizedMethod = method.toUpperCase();
  const requestHeaders = new Headers({ Accept: "application/json", ...headers });
  const isFormData = typeof FormData !== "undefined" && body instanceof FormData;

  if (body !== undefined && !isFormData) requestHeaders.set("Content-Type", "application/json");
  if (MUTATING_METHODS.has(normalizedMethod)) {
    const csrf = await ensureCsrfToken();
    requestHeaders.set(csrf.headerName, csrf.token);
  }

  let response;
  try {
    response = await fetch(apiUrl(path), {
      method: normalizedMethod,
      credentials: "include",
      headers: requestHeaders,
      body: body === undefined ? undefined : (isFormData ? body : JSON.stringify(body)),
      signal,
    });
  } catch {
    throw new ApiError(safeErrorMessage(0), { status: 0 });
  }

  if (!response.ok) {
    if ((response.status === 401 || response.status === 403) && MUTATING_METHODS.has(normalizedMethod)) {
      clearCsrfToken();
    }
    throw await createApiError(response, normalizedMethod);
  }

  if (response.status === 204) return null;
  if (responseType === "blob") return response.blob();
  if (responseType === "text") return response.text();
  return parseJsonSafely(response);
}
