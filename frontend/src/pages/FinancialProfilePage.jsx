import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/apiClient";
import {
  createProfile,
  getCurrentProfile,
  PROFILE_FIELDS,
  updateProfile,
} from "../api/financialProfileApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import {
  EMPTY_PROFILE_VALUES,
  FinancialProfileForm,
} from "../components/form/FinancialProfileForm";

function mapFieldErrors(fieldErrors) {
  const allowed = new Set(PROFILE_FIELDS);
  return Object.fromEntries(
    fieldErrors
      .filter(({ field }) => allowed.has(field))
      .map(({ field, message }) => [field, message || "입력값을 확인해주세요."]),
  );
}

export function FinancialProfilePage({ mode }) {
  const navigate = useNavigate();
  const [initialValues, setInitialValues] = useState(EMPTY_PROFILE_VALUES);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [conflict, setConflict] = useState(false);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    setConflict(false);
    try {
      const profile = await getCurrentProfile();
      if (mode === "create" && profile) {
        navigate("/profile/summary", { replace: true });
        return;
      }
      if (mode === "edit" && !profile) {
        navigate("/profile/setup", { replace: true });
        return;
      }
      if (profile) setInitialValues(profile);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [mode, navigate]);

  useEffect(() => { void loadProfile(); }, [loadProfile]);

  async function handleSubmit(values) {
    try {
      const saved = mode === "create" ? await createProfile(values) : await updateProfile(values);
      navigate("/profile/summary", {
        replace: true,
        state: { saveStatus: mode === "create" ? "created" : "updated", version: saved.version },
      });
      return null;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 409) {
          setConflict(true);
          return { formError: "다른 변경이 먼저 저장되었습니다. 최신 프로필을 불러와 다시 확인해주세요." };
        }
        if (error.fieldErrors.length > 0) {
          return {
            fieldErrors: mapFieldErrors(error.fieldErrors),
            formError: "서버 검증을 통과하지 못한 항목을 확인해주세요.",
          };
        }
        if (error.status === 401 || error.status === 403) {
          return { formError: "인증 또는 보안 세션이 만료되었습니다. 다시 로그인하거나 다시 제출해주세요." };
        }
        return { formError: error.message };
      }
      return { formError: "프로필을 저장하지 못했습니다. 다시 시도해주세요." };
    }
  }

  if (loading) return <LoadingScreen label="현재 금융 프로필을 확인하고 있습니다." />;
  if (loadError) {
    return (
      <StatusMessage
        tone="error"
        title="금융 프로필을 불러오지 못했습니다"
        description="서버 연결을 확인한 뒤 다시 시도해주세요."
        actionLabel="다시 불러오기"
        onAction={loadProfile}
      />
    );
  }

  const creating = mode === "create";
  return (
    <section className="content-page profile-editor">
      <div className="page-heading">
        <div>
          <p className="eyebrow">{creating ? "FINANCIAL PROFILE ONBOARDING" : "UPDATE FINANCIAL STATE"}</p>
          <h1>{creating ? "첫 Financial Twin을 위한 기준점" : "현재 금융 상태 수정"}</h1>
          <p>{creating ? "계좌 원문 없이 핵심 숫자만 입력하면 분석의 기준 상태가 만들어집니다." : "수정 시 기존 프로필은 보존되고 새로운 불변 버전이 생성됩니다."}</p>
        </div>
        {!creating && <span className="version-pill">새 버전 생성</span>}
      </div>
      <div className="privacy-callout"><strong>Privacy sealed</strong><span>금융기관명·계좌번호·거래 원문을 입력하지 마세요.</span></div>
      {conflict && (
        <div className="form-banner form-banner--warning" role="alert">
          최신 프로필과 충돌했습니다.
          <button className="text-button" type="button" onClick={loadProfile}>최신 값 다시 불러오기</button>
        </div>
      )}
      <FinancialProfileForm
        initialValues={initialValues}
        onSubmit={handleSubmit}
        submitLabel={creating ? "Financial Profile 만들기" : "새 버전으로 저장"}
      />
    </section>
  );
}
