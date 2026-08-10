import { Navigate, Outlet, useLocation } from "react-router-dom";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { useAuth } from "./useAuth";

export function ProtectedRoute() {
  const { status, refreshAuth } = useAuth();
  const location = useLocation();

  if (status === "loading") return <LoadingScreen label="인증 상태를 확인하고 있습니다." />;
  if (status === "error") {
    return (
      <StatusMessage
        title="인증 서버에 연결하지 못했습니다"
        description="잠시 후 다시 시도해주세요."
        actionLabel="다시 확인"
        onAction={() => refreshAuth({ showLoading: true })}
      />
    );
  }
  if (status !== "authenticated") {
    return <Navigate to="/" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}
