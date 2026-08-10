import { useNavigate } from "react-router-dom";
import { StatusMessage } from "../components/common/StatusMessage";

export function NotFoundPage() {
  const navigate = useNavigate();
  return <StatusMessage title="페이지를 찾을 수 없습니다" description="주소를 다시 확인해주세요." actionLabel="처음으로" onAction={() => navigate("/")} />;
}
