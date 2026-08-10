import { useEffect, useRef, useState } from "react";
import { PROFILE_FIELDS, toProfilePayload } from "../../api/financialProfileApi";

const MONEY_PATTERN = /^\d{1,17}(?:\.\d{1,2})?$/;
const RATE_PATTERN = /^\d{1,3}(?:\.\d{1,4})?$/;

export const EMPTY_PROFILE_VALUES = Object.freeze(
  Object.fromEntries(PROFILE_FIELDS.map((field) => [field, ""])),
);

const SECTIONS = [
  {
    title: "월 현금흐름",
    description: "세후 기준의 한 달 평균 금액을 입력해주세요.",
    fields: [
      ["monthlyIncome", "월 소득", "급여와 정기 소득의 월평균", "원"],
      ["monthlyFixedExpenses", "월 고정지출", "주거비·보험·통신비 등", "원"],
      ["monthlyVariableExpenses", "월 변동지출", "식비·쇼핑·여가비 등", "원"],
      ["monthlySavings", "월 저축액", "예·적금으로 이동하는 금액", "원"],
      ["monthlyInvestments", "월 투자액", "주식·펀드 등 투자 금액", "원"],
    ],
  },
  {
    title: "자산",
    description: "현재 시점의 잔액을 금융 유형별로 나눠주세요.",
    fields: [
      ["cashAssets", "현금성 자산", "현금과 바로 사용할 수 있는 잔액", "원"],
      ["deposits", "예금", "정기예금·적금 등 예치 잔액", "원"],
      ["investmentAssets", "투자자산", "주식·펀드·채권 등의 현재 잔액", "원"],
    ],
  },
  {
    title: "부채",
    description: "대출 잔액과 적용 중인 연 금리를 입력해주세요.",
    fields: [
      ["totalLoanBalance", "총 대출잔액", "상환하지 않은 전체 원금", "원"],
      ["loanInterestRate", "대출금리", "연 이자율, 0 이상 100 이하", "%"],
    ],
  },
];

function isRateAtMostOneHundred(value) {
  const [integer, fraction = ""] = value.split(".");
  const normalized = integer.replace(/^0+(?=\d)/, "");
  if (normalized.length < 3) return true;
  if (normalized.length > 3 || normalized > "100") return false;
  return normalized !== "100" || /^0*$/.test(fraction);
}

export function validateProfile(values) {
  const errors = {};
  for (const field of PROFILE_FIELDS) {
    const value = String(values[field] ?? "").trim();
    if (!value) {
      errors[field] = "필수 입력값입니다.";
      continue;
    }
    if (field === "loanInterestRate") {
      if (!RATE_PATTERN.test(value) || !isRateAtMostOneHundred(value)) {
        errors[field] = "0 이상 100 이하, 소수점 넷째 자리까지 입력해주세요.";
      }
    } else if (!MONEY_PATTERN.test(value)) {
      errors[field] = "0 이상의 금액을 소수점 둘째 자리까지 입력해주세요.";
    }
  }
  return errors;
}

export function FinancialProfileForm({ initialValues, onSubmit, submitLabel }) {
  const [values, setValues] = useState(() => ({ ...EMPTY_PROFILE_VALUES, ...initialValues }));
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  useEffect(() => {
    setValues({ ...EMPTY_PROFILE_VALUES, ...initialValues });
    setErrors({});
    setFormError("");
  }, [initialValues]);

  function handleChange(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    if (submittingRef.current) return;

    const clientErrors = validateProfile(values);
    if (Object.keys(clientErrors).length > 0) {
      setErrors(clientErrors);
      setFormError("입력하지 않았거나 형식이 올바르지 않은 항목이 있습니다.");
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    setErrors({});
    setFormError("");
    try {
      const result = await onSubmit(toProfilePayload(values));
      if (result?.fieldErrors) setErrors(result.fieldErrors);
      if (result?.formError) setFormError(result.formError);
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <form className="profile-form" onSubmit={handleSubmit} noValidate>
      {formError && <div className="form-banner form-banner--error" role="alert">{formError}</div>}
      {SECTIONS.map((section) => (
        <fieldset key={section.title} className="form-section">
          <legend>{section.title}</legend>
          <p className="section-help">{section.description}</p>
          <div className="field-grid">
            {section.fields.map(([name, label, help, unit]) => {
              const helpId = `${name}-help`;
              const errorId = `${name}-error`;
              return (
                <div className="form-field" key={name}>
                  <label htmlFor={name}>{label}<span aria-hidden="true"> *</span></label>
                  <div className={errors[name] ? "input-with-unit input-with-unit--error" : "input-with-unit"}>
                    <input
                      id={name}
                      name={name}
                      type="text"
                      inputMode="decimal"
                      autoComplete="off"
                      value={values[name]}
                      onChange={handleChange}
                      aria-invalid={Boolean(errors[name])}
                      aria-describedby={`${helpId}${errors[name] ? ` ${errorId}` : ""}`}
                    />
                    <span>{unit}</span>
                  </div>
                  <p id={helpId} className="field-help">{help}</p>
                  {errors[name] && <p id={errorId} className="field-error" role="alert">{errors[name]}</p>}
                </div>
              );
            })}
          </div>
        </fieldset>
      ))}
      <div className="form-actions">
        <p><strong>정밀도 안내</strong><br />금액은 소수점 둘째 자리, 금리는 넷째 자리까지 저장됩니다.</p>
        <button className="button button--primary button--large" type="submit" disabled={submitting}>
          {submitting ? "저장하고 있습니다…" : submitLabel}
        </button>
      </div>
    </form>
  );
}
