import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import { getCurrentProfile, updateProfile } from "../api/financialProfileApi";
import {
  analyzePatternFile,
  downloadXlsxTemplate,
  validatePatternFile,
} from "../api/patternAnalysisApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { FinancialProfileForm } from "../components/form/FinancialProfileForm";
import {
  DRAFT_FIELD_MAP,
  PatternAnalysisResults,
} from "../components/pattern/PatternAnalysisResults";

const DRAFT_TO_PROFILE_FIELD = Object.freeze({ monthlyInvestment: "monthlyInvestments" });

function safeUploadMessage(error) {
  if (!(error instanceof ApiError)) return "거래내역을 분석하지 못했습니다. 잠시 후 다시 시도해 주세요.";
  if (error.status === 401 || error.status === 403) {
    return "인증 또는 보안 세션이 만료되었습니다. 다시 로그인한 뒤 재시도해 주세요.";
  }
  if (error.code?.includes("SHEET")) return "transactions 시트 하나만 포함한 표준 양식을 사용해 주세요.";
  if (error.code?.includes("HEADER")) return "필수 Header와 지원 Header를 표준 양식에 맞춰 주세요.";
  if (error.code?.includes("FORMULA")) return "수식 셀은 사용할 수 없습니다. 계산된 값을 텍스트로 입력해 주세요.";
  if (error.code?.includes("MACRO")) return "매크로나 실행 코드가 포함된 파일은 사용할 수 없습니다.";
  if (error.code?.includes("EXTERNAL_LINK") || error.code?.includes("EMBEDDED")) {
    return "외부 링크나 임베디드 객체가 없는 표준 파일을 사용해 주세요.";
  }
  if (error.code?.includes("TOO_LARGE") || error.code?.includes("ARCHIVE_LIMIT")) {
    return "파일 크기 또는 압축 구조가 안전 처리 한도를 초과했습니다.";
  }
  if (error.code?.includes("DATE")) return "거래 날짜를 YYYY-MM-DD 형식의 유효한 날짜로 확인해 주세요.";
  if (error.code?.includes("AMOUNT")) return "거래 금액은 0보다 큰 일반 십진수여야 합니다.";
  if (error.code?.includes("WORKBOOK") || error.code?.includes("EXTENSION")) {
    return "정상적인 FinTwin 표준 CSV 또는 XLSX 파일인지 확인해 주세요.";
  }
  return error.message;
}

export function PatternAnalysisPage() {
  const navigate = useNavigate();
  const fileInputRef = useRef(null);
  const submittingRef = useRef(false);
  const [profile, setProfile] = useState(null);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [profileLoadError, setProfileLoadError] = useState(false);
  const [file, setFile] = useState(null);
  const [selectedFormat, setSelectedFormat] = useState("");
  const [fileError, setFileError] = useState("");
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState(null);
  const [selectedFields, setSelectedFields] = useState([]);
  const [reviewValues, setReviewValues] = useState(null);
  const [saveNotice, setSaveNotice] = useState("");
  const [templateError, setTemplateError] = useState("");

  const loadProfile = useCallback(async () => {
    setLoadingProfile(true);
    setProfileLoadError(false);
    try {
      setProfile(await getCurrentProfile());
    } catch {
      setProfileLoadError(true);
    } finally {
      setLoadingProfile(false);
    }
  }, []);

  useEffect(() => { void loadProfile(); }, [loadProfile]);

  function selectFile(candidate) {
    setFileError("");
    setResult(null);
    setReviewValues(null);
    setSelectedFields([]);
    setSaveNotice("");
    try {
      const format = validatePatternFile(candidate);
      setFile(candidate);
      setSelectedFormat(format.toUpperCase());
    } catch (error) {
      setFile(null);
      setSelectedFormat("");
      setFileError(safeUploadMessage(error));
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  function handleDrop(event) {
    event.preventDefault();
    const candidate = event.dataTransfer.files?.[0];
    if (candidate) selectFile(candidate);
  }

  async function handleAnalyze(event) {
    event.preventDefault();
    if (submittingRef.current) return;
    try {
      validatePatternFile(file);
    } catch (error) {
      setFileError(safeUploadMessage(error));
      return;
    }

    submittingRef.current = true;
    setAnalyzing(true);
    setFileError("");
    setResult(null);
    setReviewValues(null);
    try {
      const analysis = await analyzePatternFile(file);
      setResult(analysis);
      setSelectedFields([]);
    } catch (error) {
      setFileError(safeUploadMessage(error));
    } finally {
      setFile(null);
      setSelectedFormat("");
      if (fileInputRef.current) fileInputRef.current.value = "";
      submittingRef.current = false;
      setAnalyzing(false);
    }
  }

  async function handleTemplateDownload() {
    setTemplateError("");
    try {
      const blob = await downloadXlsxTemplate();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = "fintwin-transactions-template.xlsx";
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 0);
    } catch {
      setTemplateError("표준 XLSX 양식을 내려받지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  function toggleField(field) {
    setSelectedFields((current) => (
      current.includes(field) ? current.filter((item) => item !== field) : [...current, field]
    ));
  }

  function prepareReview() {
    if (!profile || !result?.profileDraft?.estimatedValues) return;
    const values = { ...profile };
    for (const [draftField] of DRAFT_FIELD_MAP) {
      if (!selectedFields.includes(draftField)) continue;
      values[DRAFT_TO_PROFILE_FIELD[draftField] || draftField] = String(
        result.profileDraft.estimatedValues[draftField],
      );
    }
    setReviewValues(values);
    setSaveNotice("");
  }

  async function saveReviewedProfile(values) {
    try {
      const saved = await updateProfile(values);
      setProfile(saved);
      setReviewValues(null);
      setSaveNotice(`검토한 값이 새 Snapshot v${saved.version}로 저장되었습니다.`);
      return null;
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        return { formError: "인증 또는 보안 세션이 만료되었습니다. 다시 로그인해 주세요." };
      }
      if (error instanceof ApiError && error.status === 409) {
        return { formError: "최신 Profile과 충돌했습니다. 페이지를 새로고침한 뒤 다시 검토해 주세요." };
      }
      return { formError: "새 Snapshot을 저장하지 못했습니다. 입력값과 서버 상태를 확인해 주세요." };
    }
  }

  if (loadingProfile) return <LoadingScreen label="현재 Financial Profile을 확인하고 있습니다." />;

  return (
    <section className="content-page pattern-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">PRIVATE TRANSACTION PATTERN ENGINE</p>
          <h1>거래내역 Pattern 분석</h1>
          <p>표준 CSV 또는 XLSX를 메모리에서만 정규화하고, 동일한 결정론적 Pattern Engine으로 Profile Draft를 계산합니다.</p>
        </div>
        <span className="version-pill">CSV · XLSX</span>
      </div>

      {profileLoadError && (
        <div className="form-banner form-banner--error" role="alert">
          현재 Profile을 불러오지 못했습니다. 분석은 가능하지만 Draft 반영은 Profile 확인 후 진행해 주세요.
          <button className="text-button" type="button" onClick={loadProfile}>다시 확인</button>
        </div>
      )}
      {!profileLoadError && !profile && (
        <div className="form-banner form-banner--warning" role="status">
          현재 Profile이 없습니다. 분석 후 Draft를 비교·반영하려면 Profile을 먼저 생성해 주세요.
          <button className="text-button" type="button" onClick={() => navigate("/profile/setup")}>Profile 만들기</button>
        </div>
      )}
      {saveNotice && <div className="form-banner form-banner--success" role="status">{saveNotice}</div>}

      <section className="pattern-upload-panel" aria-labelledby="pattern-upload-title">
        <div className="pattern-upload-heading">
          <div><p className="eyebrow">UPLOAD IN MEMORY</p><h2 id="pattern-upload-title">표준 거래내역 파일 선택</h2></div>
          {profile && <span>현재 Profile v{profile.version}</span>}
        </div>
        <form onSubmit={handleAnalyze}>
          <label
            className="pattern-dropzone"
            htmlFor="pattern-file"
            onDragOver={(event) => event.preventDefault()}
            onDrop={handleDrop}
          >
            <span aria-hidden="true">↓</span>
            <strong>{selectedFormat ? `${selectedFormat} 파일이 선택되었습니다.` : "파일을 놓거나 선택하세요"}</strong>
            <small>.csv 또는 .xlsx · 최대 2 MiB · 최대 10,000개 거래 · 최대 60개월</small>
            <input
              ref={fileInputRef}
              id="pattern-file"
              type="file"
              accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onChange={(event) => event.target.files?.[0] && selectFile(event.target.files[0])}
            />
          </label>
          {fileError && <p className="form-banner form-banner--error" role="alert">{fileError}</p>}
          <div className="pattern-upload-actions">
            <button className="button button--secondary" type="button" onClick={handleTemplateDownload}>XLSX Template 다운로드</button>
            <button className="button button--primary button--large" type="submit" disabled={!file || analyzing}>
              {analyzing ? "메모리에서 분석 중…" : "Pattern 분석 실행"}
            </button>
          </div>
          {templateError && <p className="field-error" role="alert">{templateError}</p>}
        </form>
      </section>

      <section className="pattern-standard-guide" aria-labelledby="standard-guide-title">
        <div><p className="eyebrow">STANDARD FORMAT</p><h2 id="standard-guide-title">transactions 시트 표준</h2></div>
        <dl>
          <div><dt>필수 Header</dt><dd>transactionDate, type, amount, category, description</dd></div>
          <div><dt>선택 Header</dt><dd>transactionId</dd></div>
          <div><dt>금지 항목</dt><dd>수식, 매크로, 외부 링크, 임베디드 객체, 개인정보 Header</dd></div>
          <div><dt>거래 유형</dt><dd>INCOME, EXPENSE, SAVING_TRANSFER, INVESTMENT_TRANSFER, DEBT_PAYMENT, TRANSFER</dd></div>
        </dl>
      </section>

      <div aria-live="polite">
        {analyzing && <p className="sr-only">거래내역을 분석하고 있습니다.</p>}
        {result && (
          <PatternAnalysisResults
            result={result}
            selectedFields={selectedFields}
            onToggleField={toggleField}
            onPrepareReview={prepareReview}
          />
        )}
      </div>

      {reviewValues && (
        <section className="pattern-profile-review" aria-labelledby="profile-review-title">
          <div className="page-heading">
            <div>
              <p className="eyebrow">EXPLICIT APPROVAL</p>
              <h2 id="profile-review-title">새 Snapshot 저장 전 최종 검토</h2>
              <p>선택하지 않은 값은 현재 Profile 그대로 유지됩니다. 저장 버튼을 눌러야 새 불변 Snapshot이 생성됩니다.</p>
            </div>
          </div>
          <FinancialProfileForm
            initialValues={reviewValues}
            onSubmit={saveReviewedProfile}
            submitLabel="검토 완료 후 새 Snapshot 저장"
          />
        </section>
      )}
    </section>
  );
}
