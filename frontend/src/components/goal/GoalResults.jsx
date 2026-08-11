import { useMemo, useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatWon } from "../../utils/money";

const GOAL_STATUS = {
  ALREADY_ACHIEVABLE: "기준안으로 달성 가능",
  ACHIEVABLE: "대안으로 달성 가능",
  PARTIALLY_ACHIEVABLE: "일부 대안만 가능",
  NOT_ACHIEVABLE: "검색 범위 내 달성 불가",
};
const PLAN_TYPES = {
  REDUCE_EXPENSE: "지출 절감",
  INCREASE_INCOME: "소득 증가",
  REDUCE_EXPENSE_AND_INVEST: "지출 절감 후 투자",
};
const PLAN_STATUS = {
  NOT_REQUIRED: "추가 행동 불필요",
  ACHIEVABLE: "달성 가능",
  INFEASIBLE: "검색 범위 내 불가능",
};
const EVENT_TYPES = {
  RECURRING_EXPENSE_CHANGE: "월 생활비 변경",
  INCOME_CHANGE: "월 소득 변경",
  INVESTMENT_CONTRIBUTION_CHANGE: "월 투자액 변경",
  ONE_TIME_EXPENSE: "일회성 지출",
  EXTRA_DEBT_REPAYMENT: "추가 대출상환",
  INCOME_PAUSE: "소득 중단",
};
export const GOAL_WARNING_MESSAGES = Object.freeze({
  ALREADY_ACHIEVABLE: "현재 가정의 기준안만으로 목표 시점에 목표를 달성할 수 있습니다.",
  EXPENSE_REDUCTION_INFEASIBLE: "현재 변동지출 범위에서는 지출 절감만으로 목표에 도달하기 어렵습니다.",
  NEGATIVE_INVESTMENT_RETURN: "입력한 투자수익률이 음수이므로 투자 대안의 손실 가능성을 함께 확인해야 합니다.",
  INVESTMENT_RETURN_BELOW_DEPOSIT_RATE: "입력한 투자수익률이 예금이율보다 낮습니다.",
  CASH_SHORTFALL: "일부 월에 가용 현금이 부족한 결과가 발생했습니다.",
  NEGATIVE_AMORTIZATION: "일부 월에 대출 원금이 줄지 않는 음의 상환이 발생했습니다.",
  INCOME_INCREASE_EXCEEDS_CURRENT_INCOME: "필요한 월 소득 증가액이 현재 월 소득보다 큽니다.",
  SEARCH_LIMIT_REACHED: "설정된 Solver 검색 한도 안에서 더 작은 행동 금액을 찾지 못했습니다.",
  INVESTMENT_CONTRIBUTION_CASH_LIMITED: "일부 월의 투자 납입액이 실제 가용 현금에 의해 제한됐습니다.",
});
const METRICS = { netWorth: "순자산", liquidAssets: "유동자산", investmentAssets: "투자자산", remainingDebt: "부채" };

function yearMonth(value) {
  const [year, month] = String(value ?? "").split("-");
  return year && month ? `${year}.${month}` : String(value ?? "-");
}
function chartNumber(value) {
  if (value == null || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}
function compactWon(value) {
  return `${new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(value)}원`;
}
function warningMessage(code) {
  return GOAL_WARNING_MESSAGES[code]
    ?? "계산 과정에서 확인이 필요한 조건이 반환됐습니다. 입력 가정과 대안 상세를 함께 확인해주세요.";
}
function exactWon(value) {
  return value == null ? "API 미제공" : formatWon(value);
}

function WarningList({ warnings }) {
  if (!warnings.length) return null;
  return (
    <section className="goal-warning-panel" aria-labelledby="goal-warnings-title">
      <p className="eyebrow">WARNINGS</p><h2 id="goal-warnings-title">계산 경고</h2>
      <ul>{warnings.map((warning, index) => <li key={`${warning.code}-${index}`}><strong>{warning.code}</strong><p>{warningMessage(warning.code)}</p></li>)}</ul>
    </section>
  );
}

function GoalSummary({ result }) {
  const items = [
    ["목표 순자산", formatWon(result.targetAmount)],
    ["목표 시점", yearMonth(result.targetEndYearMonth)],
    ["현재 순자산", formatWon(result.currentNetWorth)],
    ["Baseline 최종 순자산", formatWon(result.baselineFinalNetWorth)],
    ["목표 부족 금액", formatWon(result.goalGap)],
    ["Goal Status", GOAL_STATUS[result.goalStatus] ?? result.goalStatus],
  ];
  return <>
    <section className={`goal-result-hero goal-status--${result.goalStatus.toLowerCase()}`}>
      <div><p className="eyebrow">GOAL REVERSE SIMULATION</p><h2>{GOAL_STATUS[result.goalStatus] ?? "목표 역산 결과"}</h2><p>현재 Profile과 입력 가정 아래의 결정론적 시뮬레이션 결과입니다.</p></div>
      <span>PROFILE V{result.financialProfileVersion}</span>
    </section>
    {result.goalStatus === "ALREADY_ACHIEVABLE" && <div className="goal-already-achievable" role="status"><strong>추가 월 행동 없이 달성 가능한 목표입니다.</strong><p>Baseline 최초 달성 시점: {yearMonth(result.baselineFirstAchievedYearMonth)}</p></div>}
    <div className="goal-summary-grid">{items.map(([label, value]) => <article key={label}><span>{label}</span><strong>{value}</strong></article>)}</div>
  </>;
}

function EventList({ events }) {
  if (!events.length) return <p className="goal-no-event">생성된 구조화 이벤트가 없습니다.</p>;
  return <div className="goal-event-list">{events.map((event, index) => <article key={`${event.eventType}-${index}`}>
    <span>{EVENT_TYPES[event.eventType] ?? event.eventType}</span><strong>{event.description}</strong>
    <dl>
      {event.effectiveYearMonth && <div><dt>적용 월</dt><dd>{yearMonth(event.effectiveYearMonth)}</dd></div>}
      {event.startYearMonth && <div><dt>적용 기간</dt><dd>{yearMonth(event.startYearMonth)} ~ {yearMonth(event.endYearMonth)}</dd></div>}
      {event.amount != null && <div><dt>금액</dt><dd>{formatWon(event.amount)}</dd></div>}
      {event.monthlyDelta != null && <div><dt>월 증감액</dt><dd>{formatWon(event.monthlyDelta)}</dd></div>}
    </dl>
  </article>)}</div>;
}

function PlanCards({ plans }) {
  return <section className="goal-section" aria-labelledby="goal-plans-title">
    <div className="goal-section-heading"><div><p className="eyebrow">PLAN OPTIONS</p><h2 id="goal-plans-title">Backend가 계산한 대안</h2></div><p>표시 순서는 Backend의 의미 순서를 유지하며 Frontend에서 순위를 계산하지 않습니다.</p></div>
    <div className="goal-plan-grid">{plans.map((plan) => <article className={`goal-plan-card goal-plan-card--${plan.planStatus.toLowerCase()}`} key={plan.planType}>
      <header><span>{PLAN_STATUS[plan.planStatus] ?? plan.planStatus}</span><h3>{PLAN_TYPES[plan.planType] ?? plan.planType}</h3></header>
      <dl className="goal-plan-metrics">
        <div><dt>필요한 월 행동 금액</dt><dd>{plan.requiredMonthlyAmount == null ? "계산 범위 내 해 없음" : formatWon(plan.requiredMonthlyAmount)}</dd></div>
        <div><dt>목표 시점 최종 순자산</dt><dd>{formatWon(plan.projectedFinalNetWorth)}</dd></div>
        <div><dt>목표 대비 Margin</dt><dd>{formatWon(plan.goalMargin)}</dd></div>
        <div><dt>Solver 반복 횟수</dt><dd>{plan.solverIterations.toLocaleString("ko-KR")}회</dd></div>
        <div><dt>최대 월 금액 검증값</dt><dd>{formatWon(plan.maximumMonthlyAmountTested)}</dd></div>
        <div><dt>최초 달성 월</dt><dd>{yearMonth(plan.firstAchievedYearMonth)}</dd></div>
      </dl>
      <EventList events={plan.generatedEvents} />
      {plan.appliedConstraints.length > 0 && <div className="goal-constraints"><strong>적용 제약</strong><p>{plan.appliedConstraints.join(" · ")}</p></div>}
      {plan.planStatus === "INFEASIBLE" && plan.warnings.length === 0 && <p className="goal-infeasible-reason">Solver 검색 범위와 제약 안에서 달성 가능한 월 행동 금액을 찾지 못했습니다.</p>}
      {plan.warnings.length > 0 && <ul className="goal-plan-warnings">{plan.warnings.map((warning, index) => <li key={`${warning.code}-${index}`}><strong>{warning.code}</strong><span>{warningMessage(warning.code)}</span></li>)}</ul>}
    </article>)}</div>
  </section>;
}

function PlanComparisonTable({ plans }) {
  return <section className="goal-section" aria-labelledby="goal-comparison-title">
    <div className="goal-section-heading"><div><p className="eyebrow">SIDE BY SIDE</p><h2 id="goal-comparison-title">대안 비교 표</h2></div></div>
    <div className="goal-table-wrap"><table><caption>Goal Reverse Simulation 대안 비교</caption>
      <thead><tr><th>대안</th><th>상태</th><th>월 필요 행동</th><th>최종 순자산</th><th>목표 Margin</th><th>최종 유동자산</th><th>최종 투자자산</th><th>최종 부채</th><th>현금 부족</th><th>음의 상환</th></tr></thead>
      <tbody>{plans.map((plan) => {
        const months = plan.projectedResult.monthlyResults;
        const finalMonth = months.at(-1);
        const cashShortfall = months.some((month) => month.cashShortfall);
        const negativeAmortization = months.some((month) => month.negativeAmortization);
        return <tr key={plan.planType}><th>{PLAN_TYPES[plan.planType] ?? plan.planType}</th><td>{PLAN_STATUS[plan.planStatus] ?? plan.planStatus}</td><td>{plan.requiredMonthlyAmount == null ? "-" : formatWon(plan.requiredMonthlyAmount)}</td><td>{formatWon(plan.projectedFinalNetWorth)}</td><td>{formatWon(plan.goalMargin)}</td><td>{finalMonth ? formatWon(finalMonth.liquidAssets) : "API 미제공"}</td><td>{finalMonth ? formatWon(finalMonth.investmentAssets) : "API 미제공"}</td><td>{finalMonth ? formatWon(finalMonth.remainingDebt) : "API 미제공"}</td><td>{cashShortfall ? "발생" : "없음"}</td><td>{negativeAmortization ? "발생" : "없음"}</td></tr>;
      })}</tbody>
    </table></div>
  </section>;
}

function GoalTooltip({ active, payload, label, metric }) {
  if (!active || !payload?.length) return null;
  const source = payload[0]?.payload;
  return <div className="chart-tooltip goal-chart-tooltip"><strong>{yearMonth(label)} · {METRICS[metric]}</strong><div><span>Baseline</span><b>{exactWon(source?.baselineExact)}</b></div><div><span>선택 대안</span><b>{exactWon(source?.planExact)}</b></div></div>;
}

function GoalChart({ baseline, plans }) {
  const availablePlans = useMemo(() => plans.filter((plan) => plan.projectedResult.monthlyResults.length > 0), [plans]);
  const [metric, setMetric] = useState("netWorth");
  const [planType, setPlanType] = useState(availablePlans[0]?.planType ?? "");
  const selectedPlan = availablePlans.find((plan) => plan.planType === planType) ?? availablePlans[0];
  if (!baseline.monthlyResults.length || !selectedPlan) return <section className="goal-section goal-chart-empty"><h2>월별 비교 데이터 없음</h2><p>API가 월별 결과를 제공한 대안만 Chart에 표시합니다.</p></section>;
  const projectedByMonth = new Map(selectedPlan.projectedResult.monthlyResults.map((month) => [month.yearMonth, month]));
  const rows = baseline.monthlyResults.map((month) => {
    const projected = projectedByMonth.get(month.yearMonth);
    return { yearMonth: month.yearMonth, baseline: chartNumber(month[metric]), baselineExact: month[metric], plan: chartNumber(projected?.[metric]), planExact: projected?.[metric] ?? null };
  });
  return <section className="goal-section" aria-labelledby="goal-chart-title">
    <div className="goal-section-heading goal-chart-controls"><div><p className="eyebrow">MONTHLY PATH</p><h2 id="goal-chart-title">Baseline과 선택 대안</h2></div><div>
      <label>대안<select aria-label="Chart 대안" value={selectedPlan.planType} onChange={(event) => setPlanType(event.target.value)}>{availablePlans.map((plan) => <option key={plan.planType} value={plan.planType}>{PLAN_TYPES[plan.planType] ?? plan.planType}</option>)}</select></label>
      <label>지표<select aria-label="Chart 지표" value={metric} onChange={(event) => setMetric(event.target.value)}>{Object.entries(METRICS).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
    </div></div>
    <div className="goal-chart" role="img" aria-label={`Baseline과 ${PLAN_TYPES[selectedPlan.planType] ?? selectedPlan.planType}의 월별 ${METRICS[metric]} 비교`}><ResponsiveContainer width="100%" height={360}><LineChart data={rows} margin={{ top: 10, right: 12, left: 4, bottom: 4 }}><CartesianGrid stroke="#e0e7df" strokeDasharray="3 5" vertical={false} /><XAxis dataKey="yearMonth" tickFormatter={yearMonth} minTickGap={28} tick={{ fontSize: 11 }} /><YAxis tickFormatter={compactWon} width={72} tick={{ fontSize: 11 }} /><Tooltip content={<GoalTooltip metric={metric} />} /><Legend /><Line name="Baseline" type="monotone" dataKey="baseline" stroke="#718078" strokeWidth={2.5} strokeDasharray="7 6" dot={false} /><Line name={PLAN_TYPES[selectedPlan.planType] ?? "선택 대안"} type="monotone" dataKey="plan" stroke="#173f32" strokeWidth={3} dot={false} /></LineChart></ResponsiveContainer></div>
    <p className="goal-chart-note">Chart 좌표에만 숫자 변환을 사용하며 Tooltip은 Backend가 반환한 금액 문자열을 표시합니다.</p>
  </section>;
}

function SolverMetadata({ metadata }) {
  return <section className="goal-solver-metadata" aria-labelledby="solver-metadata-title"><div><p className="eyebrow">SOLVER METADATA</p><h2 id="solver-metadata-title">탐색 기준</h2></div><dl>
    <div><dt>검색 해상도</dt><dd>{formatWon(metadata.searchResolution)}</dd></div><div><dt>대안별 최대 반복</dt><dd>{metadata.maximumIterationsPerPlan.toLocaleString("ko-KR")}회</dd></div><div><dt>전체 반복</dt><dd>{metadata.totalIterations.toLocaleString("ko-KR")}회</dd></div><div><dt>소득 검색 상한</dt><dd>{formatWon(metadata.incomeSearchUpperLimit)}</dd></div><div><dt>검색 알고리즘</dt><dd>{metadata.searchAlgorithm || "API 미제공"}</dd></div><div><dt>단조성 기준</dt><dd>{metadata.monotonicityBasis || "API 미제공"}</dd></div>
  </dl></section>;
}

function GoalDisclaimer({ disclaimer }) {
  return <section className="goal-disclaimer"><strong>계산 기준과 면책</strong><ul><li>FinTwin은 목표 달성을 보장하지 않습니다.</li><li>투자수익률, 소득증가율, 물가상승률은 사용자가 입력한 가정입니다.</li><li>세금, 수수료와 실제 소득 증가 가능성은 반영되지 않을 수 있습니다.</li><li>계산은 생성형 AI가 아니라 결정론적 금융 엔진이 수행합니다.</li><li>결과는 금융상품 추천이나 투자 자문이 아닙니다.</li></ul>{disclaimer && <p>{disclaimer}</p>}</section>;
}

export function GoalResults({ result }) {
  return <div className="goal-results" aria-live="polite"><GoalSummary result={result} /><WarningList warnings={result.warnings} />
    {result.plans.length > 0 ? <><PlanCards plans={result.plans} /><PlanComparisonTable plans={result.plans} /><GoalChart baseline={result.baseline} plans={result.plans} /></> : <section className="goal-section goal-no-plans" role="status"><h2>반환된 대안이 없습니다</h2><p>Backend가 반환하지 않은 대안을 Frontend에서 생성하지 않습니다.</p></section>}
    <SolverMetadata metadata={result.solverMetadata} /><GoalDisclaimer disclaimer={result.disclaimer} />
  </div>;
}
