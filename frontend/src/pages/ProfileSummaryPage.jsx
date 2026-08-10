import { useCallback, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { getCurrentProfile, getProfileHistory } from "../api/financialProfileApi";
import { LoadingScreen } from "../components/common/LoadingScreen";
import { StatusMessage } from "../components/common/StatusMessage";
import { addMoney, formatProfileDate, formatWon, subtractMoney } from "../utils/money";

const DETAIL_FIELDS = [
  ["monthlyIncome", "월 소득", "money"],
  ["monthlyFixedExpenses", "월 고정지출", "money"],
  ["monthlyVariableExpenses", "월 변동지출", "money"],
  ["monthlySavings", "월 저축액", "money"],
  ["monthlyInvestments", "월 투자액", "money"],
  ["cashAssets", "현금성 자산", "money"],
  ["deposits", "예금", "money"],
  ["investmentAssets", "투자자산", "money"],
  ["totalLoanBalance", "총 대출잔액", "money"],
  ["loanInterestRate", "대출금리", "rate"],
];

export function ProfileSummaryPage() {
  const [profile, setProfile] = useState(null);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const load = useCallback(async () => {
    setLoading(true);
    setError(false);
    try {
      const [current, versions] = await Promise.all([getCurrentProfile(), getProfileHistory()]);
      if (!current) {
        navigate("/profile/setup", { replace: true });
        return;
      }
      setProfile(current);
      setHistory(versions);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => { void load(); }, [load]);

  if (loading) return <LoadingScreen label="Financial State를 계산하고 있습니다." />;
  if (error || !profile) {
    return <StatusMessage tone="error" title="프로필 요약을 불러오지 못했습니다" description="잠시 후 다시 시도해주세요." actionLabel="다시 불러오기" onAction={load} />;
  }

  const totalAssets = addMoney(profile.cashAssets, profile.deposits, profile.investmentAssets);
  const netWorth = subtractMoney(totalAssets, profile.totalLoanBalance);
  const monthlyCommitment = addMoney(
    profile.monthlyFixedExpenses,
    profile.monthlyVariableExpenses,
    profile.monthlySavings,
    profile.monthlyInvestments,
  );
  const saved = location.state?.saveStatus;

  return (
    <section className="content-page profile-summary">
      {saved && (
        <div className="form-banner form-banner--success" role="status">
          {saved === "created" ? "첫 금융 프로필이 생성되었습니다." : "수정 내용이 새 버전으로 저장되었습니다."}
          {location.state?.version ? ` 현재 버전은 v${location.state.version}입니다.` : ""}
        </div>
      )}
      <div className="page-heading">
        <div>
          <p className="eyebrow">CURRENT FINANCIAL STATE · V{profile.version}</p>
          <h1>내 금융 프로필</h1>
          <p>{formatProfileDate(profile.createdAt)} 기준으로 저장된 최신 불변 스냅샷입니다.</p>
        </div>
        <button className="button button--secondary" type="button" onClick={() => navigate("/profile/edit")}>현재 값 수정</button>
      </div>

      <div className="summary-grid">
        <article className="summary-card summary-card--accent"><span>월 소득</span><strong>{formatWon(profile.monthlyIncome)}</strong><small>MONTHLY INFLOW</small></article>
        <article className="summary-card"><span>총 금융자산</span><strong>{formatWon(totalAssets)}</strong><small>CASH + DEPOSITS + INVESTMENTS</small></article>
        <article className="summary-card"><span>총 대출잔액</span><strong>{formatWon(profile.totalLoanBalance)}</strong><small>RATE {profile.loanInterestRate}%</small></article>
        <article className="summary-card"><span>순금융자산</span><strong>{formatWon(netWorth)}</strong><small>FINANCIAL ASSETS − LOANS</small></article>
      </div>

      <div className="profile-columns">
        <section className="detail-panel">
          <div className="panel-heading"><div><p className="eyebrow">STATE DETAILS</p><h2>기준 숫자</h2></div><span>월 약정·배분 {formatWon(monthlyCommitment)}</span></div>
          <dl className="detail-list">
            {DETAIL_FIELDS.map(([field, label, type]) => (
              <div key={field}><dt>{label}</dt><dd>{type === "rate" ? `${profile[field]}%` : formatWon(profile[field])}</dd></div>
            ))}
          </dl>
        </section>

        <aside className="goal-panel">
          <p className="eyebrow">PRIMARY GOAL</p>
          <h2>주요 금융목표</h2>
          <div className="goal-empty"><span aria-hidden="true">◎</span><strong>다음 단계에서 설정</strong><p>현재 백엔드 Financial Profile 계약에는 목표 필드가 없습니다. Goal Solver에서 별도 모델로 연결합니다.</p></div>
          <button className="button button--primary button--full" type="button" onClick={() => navigate("/twin")}>My Financial Twin 만들기</button>
        </aside>
      </div>

      <section className="history-panel">
        <div className="panel-heading"><div><p className="eyebrow">IMMUTABLE HISTORY</p><h2>버전 이력</h2></div><span>{history.length} snapshots</span></div>
        {history.length === 0 ? (
          <p className="empty-copy">저장된 버전이 없습니다.</p>
        ) : (
          <ol className="version-timeline">
            {history.map((item, index) => (
              <li key={`${item.version}-${item.createdAt}`} className={index === 0 ? "version-item version-item--current" : "version-item"}>
                <span className="version-dot" aria-hidden="true" />
                <div><strong>Version {item.version}</strong><time dateTime={item.createdAt}>{formatProfileDate(item.createdAt)}</time></div>
                <p>월 소득 {formatWon(item.monthlyIncome)} · 순금융자산 {formatWon(subtractMoney(addMoney(item.cashAssets, item.deposits, item.investmentAssets), item.totalLoanBalance))}</p>
                {index === 0 && <span className="current-badge">CURRENT</span>}
              </li>
            ))}
          </ol>
        )}
      </section>
    </section>
  );
}
