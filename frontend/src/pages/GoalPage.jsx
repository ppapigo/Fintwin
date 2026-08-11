import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import {
  goalRequestErrorMessage,
  goalTargetEndYearMonth,
  reverseSimulateGoal,
  validateGoalReverseSimulation,
} from "../api/goalReverseSimulationApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { GoalResults } from "../components/goal/GoalResults";
import { SimulationAssumptionFields } from "../components/simulation/SimulationAssumptionFields";
import { createSimulationAssumptionValues } from "../simulation/simulationAssumptions";

function createGoalValues() {
  return { ...createSimulationAssumptionValues(), goalType: "TARGET_NET_WORTH", targetAmount: "" };
}

export function GoalPage() {
  const [profileState, setProfileState] = useState("loading");
  const [profile, setProfile] = useState(null);
  const [values, setValues] = useState(createGoalValues);
  const [fieldErrors, setFieldErrors] = useState({});
  const [requestError, setRequestError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const requestInFlight = useRef(false);
  const resultRef = useRef(null);
  const navigate = useNavigate();

  const loadProfile = useCallback(async () => {
    setProfileState("loading");
    try {
      const current = await getCurrentProfile();
      if (!current) {
        setProfile(null);
        setProfileState("missing");
        return;
      }
      setProfile(current);
      setProfileState("ready");
    } catch (error) {
      setProfile(null);
      setProfileState(error instanceof ApiError && (error.status === 401 || error.status === 403)
        ? "auth-expired"
        : "error");
    }
  }, []);

  useEffect(() => { void loadProfile(); }, [loadProfile]);

  function updateValue(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: name === "horizonMonths" ? Number(value) : value }));
    setFieldErrors((current) => ({ ...current, [name]: undefined, targetEndYearMonth: undefined }));
    setRequestError("");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (requestInFlight.current || !profile) return;
    const errors = validateGoalReverseSimulation(values, profile);
    setFieldErrors(errors);
    setRequestError("");
    if (Object.keys(errors).length > 0) return;

    requestInFlight.current = true;
    setSubmitting(true);
    setResult(null);
    try {
      const response = await reverseSimulateGoal(values);
      if (response.goalStatus === "NEEDS_INPUT") {
        setRequestError("목표 역산에 필요한 입력값을 다시 확인해주세요.");
        return;
      }
      setResult(response);
      window.requestAnimationFrame?.(() => resultRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" }));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setProfile(null);
        setProfileState("missing");
        return;
      }
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setProfileState("auth-expired");
        return;
      }
      setRequestError(goalRequestErrorMessage(error));
    } finally {
      requestInFlight.current = false;
      setSubmitting(false);
    }
  }

  if (profileState === "loading") return <LoadingScreen label="최신 Financial Profile을 확인하고 있습니다." />;
  if (profileState === "missing") return <StatusMessage title="Financial Profile이 먼저 필요합니다" description="Goal Reverse Simulation은 최신 Profile을 기준으로 계산합니다." actionLabel="Profile 설정으로 이동" onAction={() => navigate("/profile/setup")} />;
  if (profileState === "auth-expired") return <StatusMessage tone="error" title="인증 세션이 만료되었습니다" description="로그인 화면에서 다시 인증한 뒤 Goal을 실행해주세요." actionLabel="로그인 화면으로 이동" onAction={() => navigate("/")} />;
  if (profileState === "error" || !profile) return <StatusMessage tone="error" title="Financial Profile을 불러오지 못했습니다" description="서버 연결을 확인한 뒤 다시 시도해주세요." actionLabel="다시 불러오기" onAction={loadProfile} />;

  const targetEnd = goalTargetEndYearMonth(values.startYearMonth, values.horizonMonths);

  return (
    <section className="content-page goal-page">
      <header className="goal-page-header">
        <div>
          <p className="eyebrow">GOAL REVERSE SIMULATION</p>
          <h1>목표에서 현재로<br />행동 금액을 역산합니다</h1>
          <p>목표 순자산과 기간을 입력하면 결정론적 금융 엔진이 Backend가 지원하는 대안을 각각 탐색합니다. 결과는 예측이나 달성 보장이 아닙니다.</p>
        </div>
        <aside><span>LATEST PROFILE</span><strong>Version {profile.version}</strong><small>결과 저장 없음</small><small>생성형 AI 계산 없음</small></aside>
      </header>

      <form className="goal-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <div className="goal-form-heading"><div><p className="eyebrow">TARGET & ASSUMPTIONS</p><h2>목표와 계산 가정</h2><p>금액과 비율은 문자열 그대로 Backend에 전달되며 Frontend에서 금융 결과를 계산하지 않습니다.</p></div><span>DETERMINISTIC · NOT GUARANTEED</span></div>

        {requestError && <div className="form-banner form-banner--error" role="alert">{requestError}</div>}
        <div className="goal-primary-fields">
          <div className="assumption-field">
            <label htmlFor="goalType">목표 유형</label>
            <input id="goalType" name="goalType" value="TARGET_NET_WORTH" readOnly aria-readonly="true" />
            <p className="field-help">현재 단계에서는 목표 순자산만 지원합니다.</p>
          </div>
          <div className="assumption-field">
            <label htmlFor="targetAmount">목표 순자산</label>
            <div className={fieldErrors.targetAmount ? "assumption-input assumption-input--error" : "assumption-input"}>
              <input id="targetAmount" name="targetAmount" type="text" inputMode="decimal" value={values.targetAmount} onChange={updateValue} disabled={submitting} aria-invalid={Boolean(fieldErrors.targetAmount)} placeholder="예: 50000000" />
              <span>원</span>
            </div>
            {fieldErrors.targetAmount && <p className="field-error">{fieldErrors.targetAmount}</p>}
          </div>
          <div className="goal-end-field">
            <span>목표 종료 연월</span>
            <output aria-live="polite">{targetEnd || "기간을 확인해주세요"}</output>
            <p>시작 월을 포함해 {values.horizonMonths}개월 후의 마지막 월입니다.</p>
            {fieldErrors.targetEndYearMonth && <p className="field-error">{fieldErrors.targetEndYearMonth}</p>}
          </div>
        </div>

        <SimulationAssumptionFields values={values} fieldErrors={fieldErrors} profile={profile} disabled={submitting} onChange={updateValue} />

        <div className="goal-submit-bar">
          <p><strong>일회성 역산 · 저장하지 않음</strong><br />Backend가 반환하지 않은 대안과 월별 값을 Frontend에서 만들지 않습니다.</p>
          <button className="button button--primary button--large" type="submit" disabled={submitting}>
            {submitting ? "대안을 탐색하는 중…" : "목표 달성 대안 계산"}
          </button>
        </div>
        <p className="sr-only" aria-live="polite">{submitting ? "Goal Reverse Simulation을 실행하고 있습니다." : ""}</p>
      </form>

      {!result && !submitting && <section className="goal-initial-state"><span aria-hidden="true">GOAL</span><div><p className="eyebrow">READY TO SOLVE</p><h2>목표와 가정을 입력해주세요</h2><p>지출 절감, 소득 증가, 지출 절감 후 투자 대안을 Backend 반환 순서대로 비교합니다.</p></div></section>}
      {result && <div className="goal-result-anchor" ref={resultRef}><GoalResults result={result} /></div>}
    </section>
  );
}
