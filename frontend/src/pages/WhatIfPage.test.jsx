import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import { compareScenario, previewScenarioPayload, runNaturalLanguageWhatIf } from "../api/whatIfApi";
import { WhatIfPage } from "./WhatIfPage";

vi.mock("../api/financialProfileApi", () => ({ getCurrentProfile: vi.fn() }));
vi.mock("../api/whatIfApi", () => ({
  compareScenario: vi.fn(),
  previewScenarioPayload: vi.fn(),
  runNaturalLanguageWhatIf: vi.fn(),
}));
vi.mock("recharts", () => ({
  ResponsiveContainer: ({ children }) => <div>{children}</div>,
  LineChart: ({ children }) => <div>{children}</div>,
  CartesianGrid: () => null,
  Legend: () => null,
  Line: () => null,
  Tooltip: () => null,
  XAxis: () => null,
  YAxis: () => null,
}));

const PROFILE = { version: 4, totalLoanBalance: "0.00" };
const SAFE_PREVIEW = {
  status: "SAFE",
  privacyMode: "STRICT",
  externalPayload: {
    sanitizedScenarioText: "[MONEY_1] 자동차를 사면?",
  },
  externalFieldNames: ["schemaVersion", "purpose", "sanitizedScenarioText"],
  referenceTypes: ["MONEY"],
  blockedIdentifierTypes: [],
};
const NATURAL_RESULT = {
  status: "COMPLETED",
  typedResult: {
    startYearMonth: "2026-08",
    horizonMonths: 60,
    baselineFinalNetWorth: "10000000.00",
    whatIfFinalNetWorth: "9000000.00",
    netWorthDelta: "-1000000.00",
    liquidAssetsDelta: "-1000000.00",
    debtDelta: "0.00",
    cumulativeIncomeDelta: "0.00",
    cumulativeConsumptionDelta: "1000000.00",
    serviceWarnings: [],
  },
  risks: [],
  explanation: null,
  metadata: { aiUsed: true, provider: "openai", model: "contract-model", privacyMode: "STRICT", financialValuesTokenized: true, toolCallCount: 1 },
  trace: [],
  privacyNotice: "strict",
  disclaimer: "예측이나 보장이 아닙니다.",
};
const MONTH = {
  monthNumber: 1, yearMonth: "2026-08", liquidAssets: "5000000.00", investmentAssets: "2000000.00",
  totalFinancialAssets: "7000000.00", remainingDebt: "0.00", netWorth: "7000000.00",
  cashShortfall: false, negativeAmortization: false,
};
const DIRECT_RESULT = {
  financialProfileVersion: 4,
  scenarioName: "직접 입력 시나리오",
  startYearMonth: "2026-08",
  horizonMonths: 60,
  assumptions: {},
  normalizedEvents: [{ eventId: "event-1", eventType: "ONE_TIME_EXPENSE", effectiveYearMonth: "2026-09", amount: "1000000.00", monthlyDelta: null, description: "자동차 계약금" }],
  baseline: { monthlyResults: [MONTH], checkpoints: [{ ...MONTH, monthNumber: 12, yearMonth: "2027-07" }], calculationBasis: { disclaimer: "예측이나 보장이 아닙니다." } },
  whatIf: { monthlyResults: [{ ...MONTH, liquidAssets: "4000000.00", totalFinancialAssets: "6000000.00", netWorth: "6000000.00" }], checkpoints: [{ ...MONTH, monthNumber: 12, yearMonth: "2027-07", liquidAssets: "4000000.00", totalFinancialAssets: "6000000.00", netWorth: "6000000.00" }] },
  checkpointComparisons: [{ monthNumber: 12, yearMonth: "2027-07", liquidAssetsDelta: "-1000000.00", investmentAssetsDelta: "0.00", totalFinancialAssetsDelta: "-1000000.00", debtDelta: "0.00", netWorthDelta: "-1000000.00" }],
  finalComparison: { netWorthDelta: "-1000000.00", debtDelta: "0.00", cumulativeIncomeDelta: "0.00", cumulativeConsumptionDelta: "1000000.00" },
  impactSummary: { consumptionDelta: "1000000.00", netWorthDelta: "-1000000.00", residualDelta: "0.00" },
  warnings: [],
};

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/what-if"]}>
      <Routes>
        <Route path="/what-if" element={<WhatIfPage />} />
        <Route path="/profile/setup" element={<h1>프로필 설정</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

async function approveSafePreview(user) {
  await screen.findByRole("heading", { name: /한 가지 선택/ });
  await user.click(screen.getByRole("button", { name: "내년에 3천만 원짜리 자동차를 사면?" }));
  await user.click(screen.getByRole("button", { name: "AI 전달 내용 확인" }));
  await user.click(await screen.findByRole("checkbox"));
}

describe("WhatIfPage", () => {
  beforeEach(() => {
    vi.mocked(getCurrentProfile).mockReset();
    vi.mocked(previewScenarioPayload).mockReset();
    vi.mocked(runNaturalLanguageWhatIf).mockReset();
    vi.mocked(compareScenario).mockReset();
    vi.mocked(getCurrentProfile).mockResolvedValue(PROFILE);
  });

  it("redirects to profile setup when the authenticated user has no profile", async () => {
    vi.mocked(getCurrentProfile).mockResolvedValue(null);
    renderPage();

    expect(await screen.findByRole("heading", { name: "프로필 설정" })).toBeInTheDocument();
    expect(previewScenarioPayload).not.toHaveBeenCalled();
    expect(compareScenario).not.toHaveBeenCalled();
  });

  it("requires a SAFE preview and explicit confirmation, then invalidates approval after editing", async () => {
    const user = userEvent.setup();
    vi.mocked(previewScenarioPayload).mockResolvedValue(SAFE_PREVIEW);
    renderPage();

    await screen.findByRole("heading", { name: /한 가지 선택/ });
    const runButton = screen.getByRole("button", { name: "시뮬레이션 실행" });
    expect(runButton).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "내년에 3천만 원짜리 자동차를 사면?" }));
    await user.click(screen.getByRole("button", { name: "AI 전달 내용 확인" }));

    expect(previewScenarioPayload).toHaveBeenCalledOnce();
    expect(await screen.findByText("[MONEY_1] 자동차를 사면?")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("30000000");
    await user.click(screen.getByRole("checkbox", { name: "토큰화된 전달 내용을 확인했습니다." }));
    expect(runButton).toBeEnabled();

    await user.type(screen.getByLabelText("What-if 문장"), " 수정");
    expect(runButton).toBeDisabled();
    expect(screen.queryByText("[MONEY_1] 자동차를 사면?")).not.toBeInTheDocument();
  });

  it("does not allow a blocked privacy preview to call the natural language endpoint", async () => {
    const user = userEvent.setup();
    vi.mocked(previewScenarioPayload).mockResolvedValue({ status: "BLOCKED", externalPayload: null, externalFieldNames: [], referenceTypes: [], blockedIdentifierTypes: ["EMAIL"], privacyMode: "STRICT" });
    renderPage();

    await screen.findByRole("heading", { name: /한 가지 선택/ });
    await user.type(screen.getByLabelText("What-if 문장"), "이메일 example@example.com으로 알려줘");
    await user.click(screen.getByRole("button", { name: "AI 전달 내용 확인" }));

    expect(await screen.findByText("외부 AI 호출이 차단됐습니다")).toBeInTheDocument();
    expect(screen.getByText("이메일")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "시뮬레이션 실행" })).toBeDisabled();
    expect(runNaturalLanguageWhatIf).not.toHaveBeenCalled();
  });

  it("renders only the natural response summary and its explicit contract limitation", async () => {
    const user = userEvent.setup();
    vi.mocked(previewScenarioPayload).mockResolvedValue(SAFE_PREVIEW);
    vi.mocked(runNaturalLanguageWhatIf).mockResolvedValue(NATURAL_RESULT);
    renderPage();

    await screen.findByRole("heading", { name: /한 가지 선택/ });
    await user.click(screen.getByRole("button", { name: "내년에 3천만 원짜리 자동차를 사면?" }));
    await user.click(screen.getByRole("button", { name: "AI 전달 내용 확인" }));
    await user.click(await screen.findByRole("checkbox"));
    await user.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(runNaturalLanguageWhatIf).toHaveBeenCalledOnce();
    expect(await screen.findByText("자연어 API는 월별 Series와 Checkpoint를 반환하지 않습니다.")).toBeInTheDocument();
    expect(screen.getByText("AI 사용 예")).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: /월별/ })).not.toBeInTheDocument();
  });

  it("renders information-gap questions and the actual zero tool-call count", async () => {
    const user = userEvent.setup();
    vi.mocked(previewScenarioPayload).mockResolvedValue(SAFE_PREVIEW);
    vi.mocked(runNaturalLanguageWhatIf).mockResolvedValue({
      status: "NEEDS_INPUT", typedResult: null, clarificationQuestions: ["자동차 구매 월을 알려주세요."],
      metadata: { toolCallCount: 0 }, risks: [], trace: [],
    });
    renderPage();

    await approveSafePreview(user);
    await user.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(await screen.findByText("자동차 구매 월을 알려주세요.")).toBeInTheDocument();
    expect(screen.getByText(/TOOL CALL 0/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Baseline과 What-if의 차이" })).not.toBeInTheDocument();
  });

  it.each(["REJECTED", "FAILED"])("keeps %s responses fail-closed without a tool result", async (status) => {
    const user = userEvent.setup();
    vi.mocked(previewScenarioPayload).mockResolvedValue(SAFE_PREVIEW);
    vi.mocked(runNaturalLanguageWhatIf).mockResolvedValue({ status, typedResult: null, clarificationQuestions: [], metadata: { toolCallCount: 0 }, risks: [], trace: [] });
    renderPage();

    await approveSafePreview(user);
    await user.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(await screen.findByText(status)).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "자연어 실행을 완료하지 않았습니다" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Baseline과 What-if의 차이" })).not.toBeInTheDocument();
  });

  it("offers the direct-input fallback when the AI adapter is disabled", async () => {
    const user = userEvent.setup();
    vi.mocked(previewScenarioPayload).mockResolvedValue(SAFE_PREVIEW);
    vi.mocked(runNaturalLanguageWhatIf).mockRejectedValue(new ApiError("provider detail", { status: 503, code: "AI_DISABLED" }));
    renderPage();

    await approveSafePreview(user);
    await user.click(screen.getByRole("button", { name: "시뮬레이션 실행" }));

    expect(await screen.findByText(/현재 자연어 해석 기능을 사용할 수 없습니다/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "직접 입력으로 이동" })).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("provider detail");
  });

  it("runs a direct structured event exactly once without any AI call", async () => {
    const user = userEvent.setup();
    let completeRequest;
    vi.mocked(compareScenario).mockImplementation(() => new Promise((resolve) => { completeRequest = resolve; }));
    renderPage();

    await screen.findByRole("heading", { name: /한 가지 선택/ });
    await user.click(screen.getByRole("tab", { name: /직접 입력/ }));
    await user.clear(screen.getByLabelText("설명"));
    await user.type(screen.getByLabelText("설명"), "자동차 계약금");
    await user.type(screen.getByLabelText("적용 월"), "2026-09");
    await user.type(screen.getByLabelText("지출 금액"), "1000000");
    const runButton = screen.getByRole("button", { name: "결정론적 비교 실행" });
    await user.click(runButton);

    expect(compareScenario).toHaveBeenCalledOnce();
    expect(runNaturalLanguageWhatIf).not.toHaveBeenCalled();
    expect(previewScenarioPayload).not.toHaveBeenCalled();
    expect(runButton).toBeDisabled();
    await user.click(runButton);
    expect(compareScenario).toHaveBeenCalledOnce();

    await act(async () => completeRequest(DIRECT_RESULT));
    expect(await screen.findByRole("heading", { name: "Baseline과 What-if의 차이" })).toBeInTheDocument();
    expect(screen.getByText("AI 사용 아니요")).toBeInTheDocument();
    expect(screen.getAllByText("API 미제공").length).toBeGreaterThan(0);
    expect(document.body.textContent).not.toMatch(/financialProfileId|userId/);
  });
});
