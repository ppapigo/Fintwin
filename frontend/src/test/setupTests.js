import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";
import { clearCsrfToken } from "../api/apiClient";

afterEach(() => {
  cleanup();
  clearCsrfToken();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
