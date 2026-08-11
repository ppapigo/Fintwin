import { ApiError, apiRequest } from "./apiClient";

export const MAX_PATTERN_FILE_BYTES = 2 * 1024 * 1024;
const SUPPORTED_EXTENSIONS = new Set(["csv", "xlsx"]);
const INTEGER_FIELDS = new Set([
  "includedMonthCount",
  "transactionCount",
  "deficitMonthCount",
  "detectedMonthCount",
  "totalOccurrenceCount",
  "financialProfileVersion",
  "maximumFileBytes",
  "maximumTransactionRows",
  "maximumAnalysisMonths",
  "minimumHistoryMonths",
  "recurringMinimumDistinctMonths",
  "highConfidenceMinimumMonths",
  "moneyScale",
  "percentageScale",
]);

function quoteJsonNumbers(text) {
  let result = "";
  let inString = false;
  let escaped = false;

  for (let index = 0; index < text.length;) {
    const character = text[index];
    if (inString) {
      result += character;
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === '"') inString = false;
      index += 1;
      continue;
    }
    if (character === '"') {
      inString = true;
      result += character;
      index += 1;
      continue;
    }
    if (character === "-" || (character >= "0" && character <= "9")) {
      let end = index + 1;
      while (end < text.length && /[0-9eE+.-]/.test(text[end])) end += 1;
      result += `"${text.slice(index, end)}"`;
      index = end;
      continue;
    }
    result += character;
    index += 1;
  }
  return result;
}

function normalizeIntegers(value, key = "") {
  if (Array.isArray(value)) return value.map((item) => normalizeIntegers(item));
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([childKey, childValue]) => [
        childKey,
        normalizeIntegers(childValue, childKey),
      ]),
    );
  }
  if (INTEGER_FIELDS.has(key) && typeof value === "string" && /^\d+$/.test(value)) {
    return Number(value);
  }
  return value;
}

export function parsePatternAnalysisText(text) {
  const payload = normalizeIntegers(JSON.parse(quoteJsonNumbers(text)));
  const comparison = payload?.currentProfileComparison;
  if (!comparison) return payload;
  const { financialProfileId: _removed, ...safeComparison } = comparison;
  return { ...payload, currentProfileComparison: safeComparison };
}

export function patternFileFormat(file) {
  const extension = String(file?.name || "").split(".").pop().toLowerCase();
  return SUPPORTED_EXTENSIONS.has(extension) ? extension : null;
}

export function validatePatternFile(file) {
  if (!(file instanceof File) || file.size === 0) {
    throw new ApiError("분석할 거래내역 파일을 선택해 주세요.", { code: "FILE_REQUIRED" });
  }
  const format = patternFileFormat(file);
  if (!format) {
    throw new ApiError("FinTwin 표준 CSV 또는 XLSX 파일만 사용할 수 있습니다.", {
      code: "UNSUPPORTED_FILE_TYPE",
    });
  }
  if (file.size > MAX_PATTERN_FILE_BYTES) {
    throw new ApiError("거래내역 파일은 2 MiB 이하여야 합니다.", { code: "FILE_TOO_LARGE" });
  }
  return format;
}

export async function analyzePatternFile(file) {
  const format = validatePatternFile(file);
  const formData = new FormData();
  formData.append("file", file);
  const text = await apiRequest(`/api/patterns/analyze-${format}`, {
    method: "POST",
    body: formData,
    responseType: "text",
  });
  return parsePatternAnalysisText(text);
}

export function downloadXlsxTemplate() {
  return apiRequest("/api/patterns/xlsx-template", { responseType: "blob" });
}
