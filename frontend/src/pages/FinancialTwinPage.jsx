import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { runBaselineSimulation, validateBaselineSimulation } from "../api/baselineSimulationApi";
import { getCurrentProfile } from "../api/financialProfileApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { BaselineResults } from "../components/simulation/BaselineResults";
import { formatWon } from "../utils/money";

const RATE_FIELDS = [
  ["annualIncomeGrowthRate", "연 소득 증가율", "매월 복리로 소득에 반영됩니다."],
  ["annualInflationRate", "연 물가상승률", "고정·변동지출에 매월 복리로 반영됩니다."],
  ["annualDepositInterestRate", "연 예금이율", "월초 유동자산에 적용됩니다."],
  ["annualInvestmentReturnRate", "연 투자수익률", "수익 또는 손실 가정이며 예측값이 아닙니다."],
];

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function initialValues() {
  return {
    startYearMonth: currentYearMonth(),
    horizonMonths: 60,
    annualIncomeGrowthRate: "0",
    annualInflationRate: "0",
    annualDepositInterestRate: "0",
    annualInvestmentReturnRate: "0",
    monthlyDebtPayment: "0",
  };
}

function hasDebt(value) {
  return !/^0+(?:\.0+)?$/.test(String(value ?? "0"));
}

export function FinancialTwinPage() {
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState(false);
  const [values, setValues] = useState(initialValues);
  const [fieldErrors, setFieldErrors] = useState({});
  const [requestError, setRequestError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);
  const requestInFlight = useRef(false);
  const resultRef = useRef(null);
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

  function updateValue(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: name === "horizonMonths" ? Number(value) : value }));
    setFieldErrors((current) => ({ ...current, [name]: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (requestInFlight.current) return;

    const errors = validateBaselineSimulation(values);
    setFieldErrors(errors);
    setRequestError("");
    if (Object.keys(errors).length > 0) return;

    requestInFlight.current = true;
    setSubmitting(true);
    try {
      const response = await runBaselineSimulation(values);
      setResult(response);
      window.requestAnimationFrame?.(() => resultRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" }));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        navigate("/profile/setup", { replace: true });
        return;
      }
      setRequestError(error instanceof ApiError ? error.message : "시뮬레이션을 실행하지 못했습니다. 다시 시도해주세요.");
    } finally {
      requestInFlight.current = false;
      setSubmitting(false);
    }
  }

  if (profileLoading) return <LoadingScreen label="최신 Financial Profile을 확인하고 있습니다." />;
  if (profileError || !profile) {
    return <StatusMessage tone="error" title="Financial Profile을 불러오지 못했습니다" description="잠시 후 다시 시도해주세요." actionLabel="다시 불러오기" onAction={loadProfile} />;
  }

  const profileHasDebt = hasDebt(profile.totalLoanBalance);

  return (
    <section className="content-page financial-twin-page">
      <div className="twin-page-heading">
        <div>
          <p className="eyebrow">MY FINANCIAL TWIN · BASELINE</p>
          <h1>숫자로 먼저 보는<br />나의 5년</h1>
          <p>최신 Financial Profile Version {profile.version}을 기준으로 직접 입력한 가정만 월별 상태에 반영합니다. AI 예측이나 상품 추천은 사용하지 않습니다.</p>
        </div>
        <aside>
          <span>LATEST PROFILE</span><strong>Version {profile.version}</strong>
          <small>월 소득 {formatWon(profile.monthlyIncome)}</small>
          <small>현재 부채 {formatWon(profile.totalLoanBalance)}</small>
        </aside>
      </div>

      <form className="assumption-panel" onSubmit={handleSubmit} noValidate aria-busy={submitting}>
        <div className="assumption-heading">
          <div><p className="eyebrow">ASSUMPTIONS</p><h2>계산 가정</h2><p>모든 값은 실행 전 직접 확인할 수 있으며 결과와 함께 적용값을 표시합니다.</p></div>
          <span className="deterministic-badge">DETERMINISTIC · NOT FORECAST</span>
        </div>

        {requestError && <div className="form-banner form-banner--error" role="alert">{requestError}</div>}

        <div className="assumption-primary-grid">
          <div className="assumption-field">
            <label htmlFor="startYearMonth">시작 연월</label>
            <input id="startYearMonth" name="startYearMonth" type="month" value={values.startYearMonth} onChange={updateValue} disabled={submitting} aria-invalid={Boolean(fieldErrors.startYearMonth)} />
            {fieldErrors.startYearMonth && <p className="field-error">{fieldErrors.startYearMonth}</p>}
          </div>
          <fieldset className="horizon-field">
            <legend>기간</legend>
            <div className="horizon-options">
              {[12, 36, 60].map((months) => (
                <label key={months} className={values.horizonMonths === months ? "horizon-option horizon-option--active" : "horizon-option"}>
                  <input type="radio" name="horizonMonths" value={months} checked={values.horizonMonths === months} onChange={updateValue} disabled={submitting} />
                  <strong>{months / 12}년</strong><span>{months}개월</span>
                </label>
              ))}
            </div>
            {fieldErrors.horizonMonths && <p className="field-error">{fieldErrors.horizonMonths}</p>}
          </fieldset>
        </div>

        <div className="assumption-rate-grid">
          {RATE_FIELDS.map(([name, label, help]) => (
            <div className="assumption-field" key={name}>
              <label htmlFor={name}>{label}</label>
              <div className={fieldErrors[name] ? "assumption-input assumption-input--error" : "assumption-input"}>
                <input id={name} name={name} type="text" inputMode="decimal" value={values[name]} onChange={updateValue} disabled={submitting} aria-invalid={Boolean(fieldErrors[name])} />
                <span>%</span>
              </div>
              <p className="field-help">{help}</p>
              {fieldErrors[name] && <p className="field-error">{fieldErrors[name]}</p>}
            </div>
          ))}
        </div>

        <div className="debt-assumption-row">
          <div>
            <label htmlFor="monthlyDebtPayment">월 대출상환액</label>
            <p>{profileHasDebt ? `현재 대출잔액 ${formatWon(profile.totalLoanBalance)}을 기준으로 계산합니다.` : "현재 부채가 없어 엔진에서 0원으로 정규화됩니다."}</p>
          </div>
          <div>
            <div className={fieldErrors.monthlyDebtPayment ? "assumption-input assumption-input--error" : "assumption-input"}>
              <input id="monthlyDebtPayment" name="monthlyDebtPayment" type="text" inputMode="decimal" value={values.monthlyDebtPayment} onChange={updateValue} disabled={submitting} aria-invalid={Boolean(fieldErrors.monthlyDebtPayment)} />
              <span>원/월</span>
            </div>
            {fieldErrors.monthlyDebtPayment && <p className="field-error">{fieldErrors.monthlyDebtPayment}</p>}
          </div>
        </div>

        <div className="assumption-actions">
          <p><strong>저장하지 않는 일회성 계산</strong><br />같은 Profile과 가정에는 같은 결과가 나옵니다.</p>
          <button className="button button--primary button--large" type="submit" disabled={submitting}>
            {submitting ? "계산하는 중…" : `${values.horizonMonths}개월 Baseline 실행`}
          </button>
        </div>
      </form>

      {!result && (
        <section className="twin-empty-state">
          <span aria-hidden="true">60</span>
          <div><p className="eyebrow">READY TO SIMULATE</p><h2>아직 미래를 단정하지 않습니다</h2><p>가정을 확인하고 실행하면 1·3·5년 Checkpoint와 월별 현금흐름을 한 화면에서 비교할 수 있습니다.</p></div>
        </section>
      )}

      {result && <div ref={resultRef} className="result-anchor"><BaselineResults result={result} /></div>}
    </section>
  );
}
