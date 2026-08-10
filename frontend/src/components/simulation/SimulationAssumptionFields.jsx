import { RATE_FIELDS, profileHasDebt } from "../../simulation/simulationAssumptions";
import { formatWon } from "../../utils/money";

export function SimulationAssumptionFields({ values, fieldErrors, profile, disabled, onChange }) {
  const hasDebt = profileHasDebt(profile?.totalLoanBalance);
  return (
    <>
      <div className="assumption-primary-grid">
        <div className="assumption-field">
          <label htmlFor="startYearMonth">시작 연월</label>
          <input id="startYearMonth" name="startYearMonth" type="month" value={values.startYearMonth} onChange={onChange} disabled={disabled} aria-invalid={Boolean(fieldErrors.startYearMonth)} />
          {fieldErrors.startYearMonth && <p className="field-error">{fieldErrors.startYearMonth}</p>}
        </div>
        <fieldset className="horizon-field">
          <legend>기간</legend>
          <div className="horizon-options">
            {[12, 36, 60].map((months) => (
              <label key={months} className={values.horizonMonths === months ? "horizon-option horizon-option--active" : "horizon-option"}>
                <input type="radio" name="horizonMonths" value={months} checked={values.horizonMonths === months} onChange={onChange} disabled={disabled} />
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
              <input id={name} name={name} type="text" inputMode="decimal" value={values[name]} onChange={onChange} disabled={disabled} aria-invalid={Boolean(fieldErrors[name])} />
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
          <p>{hasDebt ? `현재 대출잔액 ${formatWon(profile.totalLoanBalance)}을 기준으로 계산합니다.` : "현재 부채가 없어 엔진에서 0원으로 정규화됩니다."}</p>
        </div>
        <div>
          <div className={fieldErrors.monthlyDebtPayment ? "assumption-input assumption-input--error" : "assumption-input"}>
            <input id="monthlyDebtPayment" name="monthlyDebtPayment" type="text" inputMode="decimal" value={values.monthlyDebtPayment} onChange={onChange} disabled={disabled} aria-invalid={Boolean(fieldErrors.monthlyDebtPayment)} />
            <span>원/월</span>
          </div>
          {fieldErrors.monthlyDebtPayment && <p className="field-error">{fieldErrors.monthlyDebtPayment}</p>}
        </div>
      </div>
    </>
  );
}
