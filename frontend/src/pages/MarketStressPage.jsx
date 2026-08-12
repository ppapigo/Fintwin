import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile } from "../api/financialProfileApi";
import {
  createMarketStressValues,
  getMarketContext,
  runMarketStress,
  validateMarketStress,
} from "../api/marketStressApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { MarketContextPanel } from "../components/marketstress/MarketContextPanel";
import { MarketStressResults } from "../components/marketstress/MarketStressResults";
import { SimulationAssumptionFields } from "../components/simulation/SimulationAssumptionFields";
import { formatWon } from "../utils/money";

const UNAVAILABLE_CONTEXT = { status: "UNAVAILABLE", observations: [] };

export function MarketStressPage() {
  const [profileState, setProfileState] = useState("loading");
  const [profile, setProfile] = useState(null);
  const [context, setContext] = useState(UNAVAILABLE_CONTEXT);
  const [values, setValues] = useState(createMarketStressValues);
  const [fieldErrors, setFieldErrors] = useState({});
  const [requestError, setRequestError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const requestInFlight = useRef(false);
  const resultRef = useRef(null);
  const navigate = useNavigate();

  const loadPage = useCallback(async () => {
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
      try {
        setContext(await getMarketContext());
      } catch {
        setContext(UNAVAILABLE_CONTEXT);
      }
    } catch (error) {
      setProfile(null);
      setProfileState(error instanceof ApiError && (error.status === 401 || error.status === 403)
        ? "auth-expired" : "error");
    }
  }, []);

  useEffect(() => { void loadPage(); }, [loadPage]);

  function updateValue(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: name === "horizonMonths" ? Number(value) : value }));
    setFieldErrors((current) => ({ ...current, [name]: undefined, exposure: undefined }));
    setRequestError("");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (requestInFlight.current || !profile) return;
    const errors = validateMarketStress(values, profile);
    setFieldErrors(errors);
    setRequestError("");
    if (Object.keys(errors).length > 0) return;

    requestInFlight.current = true;
    setSubmitting(true);
    setResult(null);
    try {
      setResult(await runMarketStress(values));
      window.requestAnimationFrame?.(() => resultRef.current?.scrollIntoView?.({
        behavior: "smooth", block: "start",
      }));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setProfileState("missing");
        return;
      }
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        setProfileState("auth-expired");
        return;
      }
      setRequestError(error instanceof ApiError
        ? error.message : "시장 Stress Simulation을 실행하지 못했습니다. 다시 시도해주세요.");
    } finally {
      requestInFlight.current = false;
      setSubmitting(false);
    }
  }

  if (profileState === "loading") return <LoadingScreen label="Market Stress 입력 기반을 확인하고 있습니다." />;
  if (profileState === "missing") return <StatusMessage title="Financial Profile이 먼저 필요합니다" description="Market Stress Simulation은 최신 Profile의 자산·부채를 기준으로 계산합니다." actionLabel="Profile 설정으로 이동" onAction={() => navigate("/profile/setup")} />;
  if (profileState === "auth-expired") return <StatusMessage tone="error" title="인증 세션이 만료되었습니다" description="로그인 화면에서 다시 인증한 뒤 실행해주세요." actionLabel="로그인 화면으로 이동" onAction={() => navigate("/")} />;
  if (profileState === "error" || !profile) return <StatusMessage tone="error" title="Financial Profile을 불러오지 못했습니다" description="서버 연결을 확인한 뒤 다시 시도해주세요." actionLabel="다시 불러오기" onAction={loadPage} />;

  return (
    <section className="content-page market-stress-page">
      <header className="market-stress-header">
        <div><p className="eyebrow">MARKET STRESS SIMULATION</p><h1>관측과 가정을 분리해<br />충격을 검증합니다</h1><p>국내·해외 주식 Exposure, 환율과 대출금리 충격을 직접 입력하고 Baseline 대비 현금흐름·순자산·목표 영향을 확인합니다. 현재 시장값으로 미래를 예측하지 않습니다.</p></div>
        <aside><span>LATEST PROFILE</span><strong>Version {profile.version}</strong><small>투자자산 {formatWon(profile.investmentAssets)}</small><small>결과 저장 없음 · 외부 AI 0회</small></aside>
      </header>

      <MarketContextPanel context={context} />

      <form className="market-stress-form" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <div className="market-stress-form-heading"><div><p className="eyebrow">EXPLICIT STRESS ASSUMPTIONS</p><h2>Exposure와 충격 가정</h2><p>시장 관측값은 입력란에 자동 반영되지 않습니다. 금액·비율 문자열을 그대로 Backend 결정론적 엔진에 전달합니다.</p></div><span>NOT A FORECAST</span></div>
        {requestError && <div className="form-banner form-banner--error" role="alert">{requestError}</div>}

        <SimulationAssumptionFields values={values} fieldErrors={fieldErrors} profile={profile} disabled={submitting} onChange={updateValue} />

        <fieldset className="market-stress-fieldset">
          <legend>주식 Exposure</legend>
          <p>현재 Profile 투자자산 중 충격을 적용할 금액만 입력합니다. 합계는 {formatWon(profile.investmentAssets)}을 초과할 수 없습니다.</p>
          <div className="market-stress-input-grid market-stress-input-grid--two">
            <StressInput name="domesticStockAmount" label="국내 주식 Exposure" suffix="원" value={values.domesticStockAmount} error={fieldErrors.domesticStockAmount} disabled={submitting} onChange={updateValue} />
            <StressInput name="overseasStockAmount" label="해외 주식 Exposure" suffix="원" value={values.overseasStockAmount} error={fieldErrors.overseasStockAmount} disabled={submitting} onChange={updateValue} />
          </div>
          {fieldErrors.exposure && <p className="field-error" role="alert">{fieldErrors.exposure}</p>}
        </fieldset>

        <fieldset className="market-stress-fieldset">
          <legend>Stress Scenario</legend>
          <p>주식 충격은 손실 가정만 허용합니다. 환율의 양수는 원/달러 상승에 따른 해외 Exposure 원화가치 증가, 음수는 감소를 뜻합니다.</p>
          <div className="market-stress-input-grid">
            <div className="assumption-field"><label htmlFor="shockYearMonth">충격 연월</label><input id="shockYearMonth" name="shockYearMonth" type="month" value={values.shockYearMonth} onChange={updateValue} disabled={submitting} aria-invalid={Boolean(fieldErrors.shockYearMonth)} />{fieldErrors.shockYearMonth && <p className="field-error">{fieldErrors.shockYearMonth}</p>}</div>
            <StressInput name="domesticStockShockRate" label="국내 주식 충격" suffix="%" value={values.domesticStockShockRate} error={fieldErrors.domesticStockShockRate} disabled={submitting} onChange={updateValue} />
            <StressInput name="overseasStockShockRate" label="해외 주식 충격" suffix="%" value={values.overseasStockShockRate} error={fieldErrors.overseasStockShockRate} disabled={submitting} onChange={updateValue} />
            <StressInput name="krwUsdExchangeRateShockRate" label="원/달러 환율 충격" suffix="%" value={values.krwUsdExchangeRateShockRate} error={fieldErrors.krwUsdExchangeRateShockRate} disabled={submitting} onChange={updateValue} />
            <StressInput name="loanInterestRateChangePercentagePoints" label="대출금리 변화" suffix="%p" value={values.loanInterestRateChangePercentagePoints} error={fieldErrors.loanInterestRateChangePercentagePoints} disabled={submitting} onChange={updateValue} />
            <StressInput name="targetNetWorth" label="목표 순자산 (선택)" suffix="원" value={values.targetNetWorth} error={fieldErrors.targetNetWorth} disabled={submitting} onChange={updateValue} placeholder="미입력 시 Goal Margin 생략" />
          </div>
        </fieldset>

        <div className="market-stress-actions"><p><strong>Baseline 1회 + Stress 1회</strong><br />Profile Schema와 Snapshot을 변경하거나 결과를 저장하지 않습니다.</p><button className="button button--primary button--large" type="submit" disabled={submitting}>{submitting ? "Stress 계산 중…" : "Market Stress 실행"}</button></div>
      </form>

      {!result && <section className="market-stress-empty"><span aria-hidden="true">Δ</span><div><p className="eyebrow">READY TO STRESS</p><h2>충격은 예측이 아니라 질문입니다</h2><p>“이 손실과 금리 변화가 발생하면 내 금융상태는 어떻게 달라지는가?”를 명시적 가정으로 비교합니다.</p></div></section>}
      {result && <div ref={resultRef} className="result-anchor"><MarketStressResults result={result} /></div>}
      <div className="sr-only" aria-live="polite">{submitting ? "Market Stress Simulation 실행 중" : requestError}</div>
    </section>
  );
}

function StressInput({ name, label, suffix, value, error, disabled, onChange, placeholder }) {
  return (
    <div className="assumption-field"><label htmlFor={name}>{label}</label><div className={error ? "assumption-input assumption-input--error" : "assumption-input"}><input id={name} name={name} type="text" inputMode="decimal" value={value} onChange={onChange} disabled={disabled} placeholder={placeholder} aria-invalid={Boolean(error)} /><span>{suffix}</span></div>{error && <p className="field-error">{error}</p>}</div>
  );
}
