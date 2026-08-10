import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getCurrentProfile } from "../api/financialProfileApi";
import { useAuth } from "../auth/useAuth";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";

export function AuthCallbackPage() {
  const { status, refreshAuth } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [routingError, setRoutingError] = useState(false);
  const params = new URLSearchParams(location.search);
  const callbackFailed = params.get("status") !== "success";

  useEffect(() => {
    if (callbackFailed || status !== "authenticated") return;
    let active = true;
    getCurrentProfile()
      .then((profile) => {
        if (active) navigate(profile ? "/profile/summary" : "/profile/setup", { replace: true });
      })
      .catch(() => active && setRoutingError(true));
    return () => { active = false; };
  }, [callbackFailed, navigate, status]);

  if (callbackFailed) {
    return (
      <StatusMessage
        tone="error"
        title="로그인을 완료하지 못했습니다"
        description="Google 또는 Kakao 로그인 화면에서 다시 시도해주세요."
        actionLabel="로그인으로 돌아가기"
        onAction={() => navigate("/", { replace: true })}
      />
    );
  }
  if (routingError) {
    return (
      <StatusMessage
        tone="error"
        title="금융 프로필을 확인하지 못했습니다"
        description="로그인은 완료되었습니다. 잠시 후 다시 확인해주세요."
        actionLabel="다시 확인"
        onAction={() => window.location.reload()}
      />
    );
  }
  if (status === "anonymous") {
    return (
      <StatusMessage
        tone="error"
        title="인증 세션을 확인할 수 없습니다"
        description="로그인 세션이 생성되지 않았습니다. 다시 로그인해주세요."
        actionLabel="로그인으로 돌아가기"
        onAction={() => navigate("/", { replace: true })}
      />
    );
  }
  if (status === "error") {
    return (
      <StatusMessage
        tone="error"
        title="인증 상태 확인에 실패했습니다"
        description="서버 상태를 확인한 뒤 다시 시도해주세요."
        actionLabel="다시 확인"
        onAction={() => refreshAuth({ showLoading: true })}
      />
    );
  }
  return <LoadingScreen label="로그인을 확인하고 있습니다." />;
}
