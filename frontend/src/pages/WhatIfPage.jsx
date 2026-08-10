import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import { compareScenario, previewScenarioPayload, runNaturalLanguageWhatIf } from "../api/whatIfApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { SimulationAssumptionFields } from "../components/simulation/SimulationAssumptionFields";
import { EventEditor } from "../components/whatif/EventEditor";
import { PrivacyPreviewPanel } from "../components/whatif/PrivacyPreviewPanel";
import { ScenarioComparisonResults } from "../components/whatif/ScenarioComparisonResults";
import { createFinancialEvent, validateFinancialEvents } from "../simulation/financialEvents";
import { createSimulationAssumptionValues, validateSimulationAssumptions } from "../simulation/simulationAssumptions";
import { directComparisonViewModel, naturalComparisonViewModel } from "../simulation/scenarioViewModel";

const EXAMPLES = [
  "내년에 3천만 원짜리 자동차를 사면?",
  "월 생활비를 20만 원씩 1년간 줄이면?",
  "6개월 동안 퇴사하고 쉬면?",
  "월 투자액을 50만 원 늘리면?",
  "학자금대출을 500만 원 추가 상환하면?",
];

const AI_ERROR_MESSAGES = {
  AI_DISABLED: "현재 자연어 해석 기능을 사용할 수 없습니다. 직접 입력 방식으로 동일한 금융 시나리오를 실행할 수 있습니다.",
  AI_TIMEOUT: "자연어 해석 시간이 초과됐습니다. 원문은 저장되지 않으며 직접 입력 방식을 사용할 수 있습니다.",
  AI_RATE_LIMITED: "자연어 해석 요청이 일시적으로 제한됐습니다. 직접 입력 방식을 사용할 수 있습니다.",
  AI_REFUSED: "자연어 구조화가 거절됐습니다. 개인정보 없이 필요한 이벤트 조건만 입력하거나 직접 입력을 사용해주세요.",
  AI_SCHEMA_VIOLATION: "자연어 구조화 결과가 안전한 계약을 통과하지 못했습니다. 해당 응답은 실행하지 않았습니다.",
  AI_INCOMPLETE_RESPONSE: "자연어 구조화 응답이 완전하지 않아 실행하지 않았습니다.",
  AI_EMPTY_RESPONSE: "자연어 구조화 결과가 비어 있어 실행하지 않았습니다.",
  AI_PRIVACY_GUARD_REJECTED: "Privacy Boundary가 자연어 실행을 차단했습니다.",
};

function validateScenarioName(value) {
  const text = String(value ?? "").trim();
  if (!text) return "시나리오 이름을 입력해주세요.";
  if (text.length > 100) return "시나리오 이름은 100자 이하여야 합니다.";
  return "";
}

export function WhatIfPage() {
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState(false);
  const [mode, setMode] = useState("natural");
  const [values, setValues] = useState(createSimulationAssumptionValues);
  const [assumptionErrors, setAssumptionErrors] = useState({});
  const [scenarioText, setScenarioText] = useState("");
  const [preview, setPreview] = useState(null);
  const [previewedText, setPreviewedText] = useState("");
  const [previewConfirmed, setPreviewConfirmed] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [naturalState, setNaturalState] = useState(null);
  const [scenarioName, setScenarioName] = useState("직접 입력 시나리오");
  const [events, setEvents] = useState(() => [createFinancialEvent()]);
  const [eventErrors, setEventErrors] = useState({});
  const [requestError, setRequestError] = useState("");
  const [busy, setBusy] = useState("");
  const [result, setResult] = useState(null);
  const requestInFlight = useRef(false);
  const navigate = useNavigate();

  const loadProfile = useCallback(async () => {
    setProfileLoading(true);
    setProfileError(false);
    try {
      const current = await getCurrentProfile();
      if (!current) {
        navigate("/profile/setup", { replace: true });
        return;
      }
      setProfile(current);
    } catch {
      setProfileError(true);
    } finally {
      setProfileLoading(false);
    }
  }, [navigate]);

  useEffect(() => { void loadProfile(); }, [loadProfile]);

  function clearResultState() {
    setResult(null);
    setRequestError("");
    setNaturalState(null);
  }

  function switchMode(nextMode) {
    if (busy) return;
    setMode(nextMode);
    clearResultState();
  }

  function updateAssumption(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: name === "horizonMonths" ? Number(value) : value }));
    setAssumptionErrors((current) => ({ ...current, [name]: undefined }));
    clearResultState();
  }

  function updateScenarioText(value) {
    setScenarioText(value);
    setPreview(null);
    setPreviewedText("");
    setPreviewConfirmed(false);
    setPreviewError("");
    clearResultState();
  }

  async function handlePreview() {
    if (requestInFlight.current) return;
    const text = scenarioText.trim();
    if (!text || text.length > 500) {
      setPreviewError("자연어 시나리오는 1자 이상 500자 이하여야 합니다.");
      return;
    }
    requestInFlight.current = true;
    setBusy("preview");
    setPreviewError("");
    setPreviewConfirmed(false);
    setResult(null);
    try {
      const response = await previewScenarioPayload(text);
      setPreview(response);
      setPreviewedText(text);
    } catch (error) {
      setPreview(null);
      setPreviewedText("");
      setPreviewError(error instanceof ApiError ? error.message : "Privacy Preview를 확인하지 못했습니다.");
    } finally {
      requestInFlight.current = false;
      setBusy("");
    }
  }

  async function handleNaturalExecution() {
    if (requestInFlight.current) return;
    const text = scenarioText.trim();
    if (preview?.status !== "SAFE" || previewedText !== text || !previewConfirmed) {
      setRequestError("현재 문장의 SAFE Preview를 확인하고 승인해주세요.");
      return;
    }
    const errors = validateSimulationAssumptions(values);
    setAssumptionErrors(errors);
    if (Object.keys(errors).length) return;

    requestInFlight.current = true;
    setBusy("natural");
    setRequestError("");
    setNaturalState(null);
    setResult(null);
    try {
      const response = await runNaturalLanguageWhatIf(text, values);
      if (response.status === "COMPLETED" && response.typedResult) setResult(naturalComparisonViewModel(response));
      else if (["NEEDS_INPUT", "REJECTED", "FAILED"].includes(response.status)) setNaturalState(response);
      else setRequestError("예상하지 못한 자연어 응답입니다. 직접 입력 방식을 사용해주세요.");
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        navigate("/profile/setup", { replace: true });
        return;
      }
      const message = error instanceof ApiError ? AI_ERROR_MESSAGES[error.code] ?? error.message : "자연어 시나리오를 실행하지 못했습니다.";
      setRequestError(message);
    } finally {
      requestInFlight.current = false;
      setBusy("");
    }
  }

  async function handleDirectExecution() {
    if (requestInFlight.current) return;
    const commonErrors = validateSimulationAssumptions(values);
    const directErrors = validateFinancialEvents(events, values);
    const nameError = validateScenarioName(scenarioName);
    setAssumptionErrors(commonErrors);
    setEventErrors(nameError ? { ...directErrors, scenarioName: nameError } : directErrors);
    if (Object.keys(commonErrors).length || Object.keys(directErrors).length || nameError) return;

    requestInFlight.current = true;
    setBusy("direct");
    setRequestError("");
    setResult(null);
    try {
      const response = await compareScenario(scenarioName, values, events);
      setResult(directComparisonViewModel(response));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        navigate("/profile/setup", { replace: true });
        return;
      }
      setRequestError(error instanceof ApiError ? error.message : "What-if 비교를 실행하지 못했습니다.");
    } finally {
      requestInFlight.current = false;
      setBusy("");
    }
  }

  if (profileLoading) return <LoadingScreen label="What-if 기준 Profile을 확인하고 있습니다." />;
  if (profileError || !profile) return <StatusMessage tone="error" title="Financial Profile을 불러오지 못했습니다" description="잠시 후 다시 시도해주세요." actionLabel="다시 불러오기" onAction={loadProfile} />;

  const previewCurrent = previewedText === scenarioText.trim();
  const naturalReady = preview?.status === "SAFE" && previewCurrent && previewConfirmed;

  return (
    <section className="content-page whatif-page">
      <header className="whatif-page-header">
        <div><p className="eyebrow">PRIVACY-FIRST WHAT-IF</p><h1>한 가지 선택을<br />두 개의 미래로</h1><p>AI는 자연어를 구조화할 수 있지만 금융 계산은 하지 않습니다. 직접 입력과 자연어 방식 모두 로컬 결정론적 Scenario Engine으로 실행합니다.</p></div>
        <aside><span>BASELINE SOURCE</span><strong>Profile v{profile.version}</strong><small>결과 저장 없음</small><small>외부 AI 계산 없음</small></aside>
      </header>

      <div className="whatif-tabs" role="tablist" aria-label="What-if 입력 방식">
        <button type="button" role="tab" aria-selected={mode === "natural"} className={mode === "natural" ? "whatif-tab whatif-tab--active" : "whatif-tab"} onClick={() => switchMode("natural")}><span>A</span><strong>자연어 입력</strong><small>Privacy Preview → 구조화</small></button>
        <button type="button" role="tab" aria-selected={mode === "direct"} className={mode === "direct" ? "whatif-tab whatif-tab--active" : "whatif-tab"} onClick={() => switchMode("direct")}><span>B</span><strong>직접 입력</strong><small>AI 없이 구조화 이벤트 실행</small></button>
      </div>

      <section className="whatif-panel shared-assumptions" aria-labelledby="shared-assumptions-title">
        <div className="whatif-panel-heading"><div><p className="eyebrow">SHARED ASSUMPTIONS</p><h2 id="shared-assumptions-title">공통 계산 가정</h2><p>두 입력 방식이 같은 시작점과 기간을 사용합니다.</p></div><span>DETERMINISTIC</span></div>
        <SimulationAssumptionFields values={values} fieldErrors={assumptionErrors} profile={profile} disabled={Boolean(busy)} onChange={updateAssumption} />
      </section>

      {mode === "natural" ? (
        <section className="whatif-panel natural-input-panel" role="tabpanel">
          <div className="whatif-panel-heading"><div><p className="eyebrow">NATURAL LANGUAGE</p><h2>문장으로 조건 입력</h2><p>AI는 시나리오 문장을 구조화할 뿐 금융 계산을 수행하지 않습니다.</p></div><span>{scenarioText.length} / 500</span></div>
          <label className="scenario-textarea"><span>What-if 문장</span><textarea value={scenarioText} maxLength={500} onChange={(event) => updateScenarioText(event.target.value)} placeholder="예: 내년에 3천만 원짜리 자동차를 사면?" disabled={Boolean(busy)} /></label>
          <div className="scenario-examples" aria-label="예시 문장">{EXAMPLES.map((example) => <button type="button" key={example} onClick={() => updateScenarioText(example)} disabled={Boolean(busy)}>{example}</button>)}</div>
          <div className="pii-warning"><strong>개인정보를 입력하지 마세요</strong><p>계좌번호, 주민등록번호, 카드번호, 전화번호, 이메일, 이름·주소 등 개인 식별정보를 입력하지 마세요.</p></div>
          {previewError && <div className="form-banner form-banner--error" role="alert">{previewError}</div>}
          <div className="natural-actions"><button className="button button--secondary" type="button" onClick={handlePreview} disabled={Boolean(busy) || !scenarioText.trim()}>{busy === "preview" ? "로컬 보호 경계 확인 중…" : "AI 전달 내용 확인"}</button><button className="button button--primary" type="button" onClick={handleNaturalExecution} disabled={Boolean(busy) || !naturalReady}>{busy === "natural" ? "구조화·시뮬레이션 실행 중…" : "시뮬레이션 실행"}</button></div>
          <PrivacyPreviewPanel preview={previewCurrent ? preview : null} confirmed={previewConfirmed} onConfirm={setPreviewConfirmed} />
        </section>
      ) : (
        <div role="tabpanel" className="direct-input-stack">
          <section className="whatif-panel scenario-name-panel"><label htmlFor="scenarioName">시나리오 이름</label><input id="scenarioName" value={scenarioName} maxLength={100} onChange={(event) => { setScenarioName(event.target.value); setEventErrors((current) => ({ ...current, scenarioName: undefined })); clearResultState(); }} disabled={Boolean(busy)} />{eventErrors.scenarioName && <p className="field-error">{eventErrors.scenarioName}</p>}</section>
          <EventEditor events={events} onChange={(next) => { setEvents(next); setEventErrors({}); clearResultState(); }} errors={eventErrors} disabled={Boolean(busy)} />
          <div className="direct-execute-bar"><p><strong>외부 AI 호출 0회</strong><br />구조화 Event를 Scenario Engine에 직접 전달합니다.</p><button className="button button--primary button--large" type="button" onClick={handleDirectExecution} disabled={Boolean(busy)}>{busy === "direct" ? "Baseline과 What-if 계산 중…" : "결정론적 비교 실행"}</button></div>
        </div>
      )}

      {requestError && <section className="whatif-error" role="alert"><div><strong>이번 실행은 완료되지 않았습니다</strong><p>{requestError}</p></div>{mode === "natural" && <button className="button button--secondary" type="button" onClick={() => switchMode("direct")}>직접 입력으로 이동</button>}</section>}

      {naturalState?.status === "NEEDS_INPUT" && <section className="information-gap" role="status"><p className="eyebrow">NEEDS INPUT · TOOL CALL {naturalState.metadata.toolCallCount}</p><h2>조건을 더 알려주세요</h2><ul>{naturalState.clarificationQuestions.map((question) => <li key={question}>{question}</li>)}</ul><p>기본값을 생성하지 않았습니다. 원문을 수정하면 Preview부터 다시 확인합니다.</p></section>}
      {naturalState && ["REJECTED", "FAILED"].includes(naturalState.status) && <section className="information-gap" role="alert"><p className="eyebrow">{naturalState.status}</p><h2>자연어 실행을 완료하지 않았습니다</h2><p>Tool 결과를 표시하지 않습니다. 문장을 수정해 Preview부터 다시 진행하거나 직접 입력을 사용해주세요.</p></section>}

      {result && <ScenarioComparisonResults viewModel={result} />}
    </section>
  );
}
