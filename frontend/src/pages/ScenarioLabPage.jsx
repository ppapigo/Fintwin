import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import { compareMultipleScenarios, validateScenarioLab } from "../api/scenarioLabApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { ScenarioLabResults } from "../components/scenario/ScenarioLabResults";
import { SimulationAssumptionFields } from "../components/simulation/SimulationAssumptionFields";
import { EventEditor } from "../components/whatif/EventEditor";
import { createFinancialEvent } from "../simulation/financialEvents";
import { createSimulationAssumptionValues } from "../simulation/simulationAssumptions";

const SCENARIO_KEYS = Object.freeze(["B", "C", "D", "E"]);

function createScenario(key) {
  return { scenarioKey: key, label: `Scenario ${key}`, events: [createFinancialEvent()] };
}

function nextAvailableKey(scenarios) {
  return SCENARIO_KEYS.find((key) => scenarios.every((scenario) => scenario.scenarioKey !== key));
}

export function ScenarioLabPage() {
  const [profileState, setProfileState] = useState("loading");
  const [profile, setProfile] = useState(null);
  const [values, setValues] = useState(createSimulationAssumptionValues);
  const [scenarios, setScenarios] = useState(() => [createScenario("B")]);
  const [assumptionErrors, setAssumptionErrors] = useState({});
  const [scenarioErrors, setScenarioErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [requestError, setRequestError] = useState("");
  const [authExpired, setAuthExpired] = useState(false);
  const [result, setResult] = useState(null);
  const requestInFlight = useRef(false);
  const navigate = useNavigate();

  const loadProfile = useCallback(async () => {
    setProfileState("loading");
    try {
      const current = await getCurrentProfile();
      setProfile(current);
      setProfileState(current ? "ready" : "missing");
    } catch (error) {
      if (error instanceof ApiError && [401, 403].includes(error.status)) {
        setAuthExpired(true);
        setProfileState("error");
      } else {
        setProfileState("error");
      }
    }
  }, []);

  useEffect(() => { void loadProfile(); }, [loadProfile]);

  function clearResult() {
    setResult(null);
    setRequestError("");
  }

  function updateAssumption(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: name === "horizonMonths" ? Number(value) : value }));
    setAssumptionErrors((current) => ({ ...current, [name]: undefined }));
    clearResult();
  }

  function updateScenario(key, update) {
    setScenarios((current) => current.map((scenario) => scenario.scenarioKey === key
      ? { ...scenario, ...update } : scenario));
    setScenarioErrors({});
    clearResult();
  }

  function addScenario() {
    if (submitting || scenarios.length >= 4) return;
    const key = nextAvailableKey(scenarios);
    if (key) setScenarios((current) => [...current, createScenario(key)]);
    clearResult();
  }

  function cloneScenario(source) {
    if (submitting || scenarios.length >= 4) return;
    const key = nextAvailableKey(scenarios);
    if (!key) return;
    setScenarios((current) => [...current, {
      scenarioKey: key,
      label: `${source.label.trim() || `Scenario ${source.scenarioKey}`} 복사본`.slice(0, 100),
      events: source.events.map((event) => ({ ...event })),
    }]);
    clearResult();
  }

  function removeScenario(key) {
    if (submitting || scenarios.length <= 1) return;
    setScenarios((current) => current.filter((scenario) => scenario.scenarioKey !== key));
    clearResult();
  }

  async function executeComparison() {
    if (requestInFlight.current) return;
    const validation = validateScenarioLab(values, scenarios);
    setAssumptionErrors(validation.assumptionErrors);
    setScenarioErrors(validation.scenarioErrors);
    if (Object.keys(validation.assumptionErrors).length || Object.keys(validation.scenarioErrors).length) return;

    requestInFlight.current = true;
    setSubmitting(true);
    setRequestError("");
    setAuthExpired(false);
    setResult(null);
    try {
      setResult(await compareMultipleScenarios(values, scenarios));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setProfile(null);
        setProfileState("missing");
      } else if (error instanceof ApiError && [401, 403].includes(error.status)) {
        setAuthExpired(true);
      } else if (error instanceof ApiError && ["VALIDATION_FAILED", "INVALID_REQUEST"].includes(error.code)) {
        setRequestError("입력한 기간, Scenario 이름과 Event 범위를 다시 확인해주세요.");
      } else {
        setRequestError("Scenario 비교를 완료하지 못했습니다. 잠시 후 다시 시도해주세요.");
      }
    } finally {
      requestInFlight.current = false;
      setSubmitting(false);
    }
  }

  if (profileState === "loading") return <LoadingScreen label="Scenario Lab 기준 Profile을 확인하고 있습니다." />;
  if (profileState === "missing") return <StatusMessage title="Financial Profile이 먼저 필요합니다" description="Scenario Lab은 최신 Profile Snapshot을 기준으로 계산합니다." actionLabel="Profile 설정으로 이동" onAction={() => navigate("/profile/setup")} />;
  if (profileState === "error") return <StatusMessage tone="error" title={authExpired ? "인증 세션이 만료됐습니다" : "Financial Profile을 불러오지 못했습니다"} description={authExpired ? "다시 로그인한 뒤 Scenario Lab을 이용해주세요." : "잠시 후 다시 시도해주세요."} actionLabel={authExpired ? "로그인 화면으로 이동" : "다시 불러오기"} onAction={() => authExpired ? navigate("/") : loadProfile()} />;

  return (
    <section className="content-page scenario-lab-page">
      <header className="scenario-lab-header">
        <div><p className="eyebrow">DETERMINISTIC SCENARIO LAB</p><h1>한 화면에서<br />네 가지 선택 비교</h1><p>기준안 A는 현재 생활을 유지합니다. 최대 네 개의 구조화 Scenario를 같은 Profile·기간·가정으로 실행합니다.</p></div>
        <aside><span>BASELINE SOURCE</span><strong>Profile v{profile.version}</strong><small>외부 AI 호출 0회</small><small>결과·Scenario 저장 없음</small></aside>
      </header>

      <section className="whatif-panel shared-assumptions" aria-labelledby="scenario-assumptions-title">
        <div className="whatif-panel-heading"><div><p className="eyebrow">SHARED ASSUMPTIONS</p><h2 id="scenario-assumptions-title">모든 안의 공통 계산 가정</h2><p>Baseline과 모든 Scenario에 같은 기간과 가정을 적용합니다.</p></div><span>1 BASELINE + {scenarios.length} SCENARIO</span></div>
        <SimulationAssumptionFields values={values} fieldErrors={assumptionErrors} profile={profile} disabled={submitting} onChange={updateAssumption} />
      </section>

      <section className="scenario-baseline-card"><span>A</span><div><p className="eyebrow">LOCKED BASELINE</p><h2>현재 생활 유지</h2><p>삭제하거나 수정할 수 없습니다. 최신 Profile과 공통 Assumption을 그대로 실행합니다.</p></div></section>

      <div className="scenario-editor-stack">
        {scenarioErrors.scenarios && <div className="form-banner form-banner--error" role="alert">{scenarioErrors.scenarios}</div>}
        {scenarios.map((scenario) => (
          <section className="scenario-editor" key={scenario.scenarioKey} aria-labelledby={`scenario-${scenario.scenarioKey}-title`}>
            <header><span>{scenario.scenarioKey}</span><div><p className="eyebrow">STRUCTURED SCENARIO</p><h2 id={`scenario-${scenario.scenarioKey}-title`}>{scenario.label || `Scenario ${scenario.scenarioKey}`}</h2></div><div className="scenario-editor-actions"><button type="button" onClick={() => cloneScenario(scenario)} disabled={submitting || scenarios.length >= 4}>복제</button><button type="button" onClick={() => removeScenario(scenario.scenarioKey)} disabled={submitting || scenarios.length <= 1}>삭제</button></div></header>
            <div className="scenario-label-field"><label htmlFor={`scenario-${scenario.scenarioKey}-label`}>Scenario 이름</label><input id={`scenario-${scenario.scenarioKey}-label`} value={scenario.label} maxLength={100} onChange={(event) => updateScenario(scenario.scenarioKey, { label: event.target.value })} disabled={submitting} />{scenarioErrors[`${scenario.scenarioKey}.label`] && <p className="field-error">{scenarioErrors[`${scenario.scenarioKey}.label`]}</p>}</div>
            <EventEditor events={scenario.events} onChange={(events) => updateScenario(scenario.scenarioKey, { events })} errors={scenarioErrors} disabled={submitting} idPrefix={`scenario-${scenario.scenarioKey}`} errorPrefix={`${scenario.scenarioKey}.`} />
          </section>
        ))}
      </div>

      <div className="scenario-lab-actions"><button className="button button--secondary" type="button" onClick={addScenario} disabled={submitting || scenarios.length >= 4}>+ Scenario 추가 ({scenarios.length}/4)</button><div><p><strong>외부 AI 호출 0회</strong><br />공통 Baseline 1회와 Scenario별 1회만 실행합니다.</p><button className="button button--primary button--large" type="button" onClick={executeComparison} disabled={submitting}>{submitting ? "비교 시뮬레이션 실행 중…" : "Scenario Lab 실행"}</button></div></div>

      <div className="scenario-live-region" aria-live="polite">{submitting ? "Scenario 비교를 실행하고 있습니다." : result ? "Scenario 비교가 완료됐습니다." : ""}</div>
      {authExpired && <section className="whatif-error" role="alert"><div><strong>인증 세션이 만료됐습니다</strong><p>금융 결과는 표시하지 않습니다. 다시 로그인해주세요.</p></div><button className="button button--secondary" type="button" onClick={() => navigate("/")}>로그인 화면으로 이동</button></section>}
      {requestError && <section className="whatif-error" role="alert"><div><strong>이번 비교는 완료되지 않았습니다</strong><p>{requestError}</p></div></section>}
      {result && <ScenarioLabResults result={result} />}
    </section>
  );
}
