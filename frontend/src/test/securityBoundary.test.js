import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const SOURCE_ROOT = dirname(dirname(fileURLToPath(import.meta.url)));

function sourceFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === "test" ? [] : sourceFiles(path);
    return /\.(js|jsx)$/.test(entry.name) && !entry.name.endsWith(".test.js") && !entry.name.endsWith(".test.jsx") ? [path] : [];
  });
}

describe("frontend privacy and session boundary", () => {
  const productionSource = sourceFiles(SOURCE_ROOT).map((path) => readFileSync(path, "utf8")).join("\n");
  const apiSource = sourceFiles(join(SOURCE_ROOT, "api")).map((path) => readFileSync(path, "utf8")).join("\n");

  it("does not persist credentials or manually build Authorization headers", () => {
    expect(productionSource).not.toMatch(/localStorage|sessionStorage|Authorization|Bearer\s|accessToken|refreshToken|JSESSIONID/);
  });

  it("does not log potentially sensitive application data", () => {
    expect(productionSource).not.toMatch(/console\.(log|debug|info|warn|error)/);
  });

  it("does not send user or profile identifiers from the API layer", () => {
    expect(apiSource).not.toMatch(/userId|profileId/);
  });

  it("keeps OAuth initiation as browser navigation", () => {
    const authSource = readFileSync(join(SOURCE_ROOT, "api", "authApi.js"), "utf8");
    expect(authSource).toContain("locationObject.assign");
    expect(authSource).toContain("/oauth2/authorization/${provider}");
  });
});
