import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiRequest } from "./apiClient";
import {
  analyzePatternFile,
  downloadXlsxTemplate,
  parsePatternAnalysisText,
  validatePatternFile,
} from "./patternAnalysisApi";

vi.mock("./apiClient", () => ({
  ApiError: class ApiError extends Error {
    constructor(message, options = {}) {
      super(message);
      Object.assign(this, options);
    }
  },
  apiRequest: vi.fn(),
}));

describe("patternAnalysisApi", () => {
  beforeEach(() => vi.clearAllMocks());

  it("preserves financial decimals as strings and removes the internal profile id", () => {
    const result = parsePatternAnalysisText(JSON.stringify({
      transactionCount: 2,
      averages: { monthlyIncome: 9007199254740993.12 },
      currentProfileComparison: {
        financialProfileId: 8123,
        financialProfileVersion: 7,
        deltas: { monthlyIncome: -100.25 },
      },
    }).replace("9007199254740994", "9007199254740993.12"));

    expect(result.transactionCount).toBe(2);
    expect(result.averages.monthlyIncome).toBe("9007199254740993.12");
    expect(result.currentProfileComparison.financialProfileVersion).toBe(7);
    expect(result.currentProfileComparison.deltas.monthlyIncome).toBe("-100.25");
    expect(result.currentProfileComparison).not.toHaveProperty("financialProfileId");
  });

  it("uploads an xlsx as FormData without user or profile identifiers", async () => {
    apiRequest.mockResolvedValue('{"transactionCount":1,"currentProfileComparison":null}');
    const file = new File(["safe synthetic workbook"], "transactions.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    await analyzePatternFile(file);

    expect(apiRequest).toHaveBeenCalledWith("/api/patterns/analyze-xlsx", expect.objectContaining({
      method: "POST",
      responseType: "text",
    }));
    const body = apiRequest.mock.calls[0][1].body;
    expect([...body.keys()]).toEqual(["file"]);
    expect(body.has("userId")).toBe(false);
    expect(body.has("profileId")).toBe(false);
  });

  it("uses the existing csv endpoint for csv files", async () => {
    apiRequest.mockResolvedValue('{"transactionCount":1}');

    await analyzePatternFile(new File(["safe"], "transactions.csv", { type: "text/csv" }));

    expect(apiRequest).toHaveBeenCalledWith("/api/patterns/analyze-csv", expect.any(Object));
  });

  it("rejects unsupported, empty, and oversized files before upload", () => {
    expect(() => validatePatternFile(new File(["x"], "transactions.xls"))).toThrow(/CSV 또는 XLSX/);
    expect(() => validatePatternFile(new File([], "transactions.xlsx"))).toThrow(/선택/);
    expect(() => validatePatternFile(new File([new Uint8Array(2 * 1024 * 1024 + 1)], "transactions.xlsx")))
      .toThrow(/2 MiB/);
    expect(apiRequest).not.toHaveBeenCalled();
  });

  it("downloads the standard template as a blob", async () => {
    apiRequest.mockResolvedValue(new Blob(["xlsx"]));

    await downloadXlsxTemplate();

    expect(apiRequest).toHaveBeenCalledWith("/api/patterns/xlsx-template", { responseType: "blob" });
  });
});
