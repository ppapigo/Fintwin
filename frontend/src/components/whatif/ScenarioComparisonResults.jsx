import { useState } from "react";
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { EVENT_DEFINITIONS } from "../../simulation/financialEvents";
import { formatWon } from "../../utils/money";

const METRICS = {
  netWorth: { label: "순자산" },
  totalFinancialAssets: { label: "총 금융자산" },
  liquidAssets: { label: "유동자산" },
  investmentAssets: { label: "투자자산" },
  remainingDebt: { label: "부채" },
};
const CHECKPOINT_METRICS = [
  ["netWorth", "netWorthDelta", "순자산"],
  ["totalFinancialAssets", "totalFinancialAssetsDelta", "금융자산"],
  ["liquidAssets", "liquidAssetsDelta", "유동자산"],
  ["investmentAssets", "investmentAssetsDelta", "투자자산"],
  ["remainingDebt", "debtDelta", "부채"],
];
const IMPACT_FIELDS = [
  ["incomeDelta", "소득 차이"], ["consumptionDelta", "소비 차이"],
  ["debtInterestDelta", "대출이자 차이"], ["principalRepaidDelta", "원금상환 차이"],
  ["investmentContributionDelta", "투자납입 차이"], ["investmentReturnDelta", "투자손익 차이"],
  ["liquidAssetsDelta", "유동자산 차이"], ["debtDelta", "부채 차이"],
  ["netWorthDelta", "순자산 차이"], ["residualDelta", "Residual Delta"],
];
const RISK_LABELS = {
  CASH_SHORTFALL: "현금 부족", NEGATIVE_AMORTIZATION: "음의 상환",
  NET_WORTH_DECREASE: "순자산 감소", LIQUID_ASSET_DECREASE: "유동자산 감소",
  GOAL_NOT_ACHIEVED: "목표 미달성", EXPENSE_REDUCTION_INFEASIBLE: "지출 절감 제약",
  INVESTMENT_CONTRIBUTION_CASH_LIMITED: "투자 납입 현금 제한",
};

function yearMonth(value) {
  const [year, month] = String(value ?? "").split("-");
  return year && month ? `${year}.${month}` : String(value ?? "");
}

function signedClass(value) {
  const text = String(value ?? "0");
  if (text.startsWith("-")) return "delta-negative";
  if (!/^0(?:\.0+)?$/.test(text)) return "delta-positive";
  return "";
}

function chartNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function compactWon(value) {
  return `${new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(value)}원`;
}

function ComparisonTooltip({ active, payload, label, metric }) {
  if (!active || !payload?.length) return null;
  const source = payload[0]?.payload;
  return (
    <div className="chart-tooltip comparison-tooltip">
      <strong>{yearMonth(label)} · {METRICS[metric].label}</strong>
      <div><span className="baseline-color">Baseline</span><b>{formatWon(source?.baselineExact)}</b></div>
      <div><span className="whatif-color">What-if</span><b>{formatWon(source?.whatIfExact)}</b></div>
    </div>
  );
}

function DirectSummary({ viewModel }) {
  const baseline = viewModel.baseline.monthlyResults.at(-1);
  const whatIf = viewModel.whatIf.monthlyResults.at(-1);
  const comparison = viewModel.finalComparison;
  if (!baseline || !whatIf) return null;
  const cards = [
    ["Baseline 최종 순자산", baseline.netWorth, ""], ["What-if 최종 순자산", whatIf.netWorth, ""],
    ["순자산 차이", comparison.netWorthDelta, signedClass(comparison.netWorthDelta)],
    ["Baseline 유동자산", baseline.liquidAssets, ""], ["What-if 유동자산", whatIf.liquidAssets, ""],
    ["남은 부채 차이", comparison.debtDelta, signedClass(comparison.debtDelta)],
    ["누적 소득 차이", comparison.cumulativeIncomeDelta, signedClass(comparison.cumulativeIncomeDelta)],
  ];
  return <div className="comparison-summary-grid">{cards.map(([label, value, className]) => <article key={label}><span>{label}</span><strong className={className}>{formatWon(value)}</strong></article>)}</div>;
}

function NaturalSummary({ viewModel }) {
  const comparison = viewModel.finalComparison;
  if (!comparison) return null;
  const cards = [
    ["Baseline 최종 순자산", viewModel.baseline.finalNetWorth], ["What-if 최종 순자산", viewModel.whatIf.finalNetWorth],
    ["순자산 차이", comparison.netWorthDelta], ["유동자산 차이", comparison.liquidAssetsDelta],
    ["남은 부채 차이", comparison.debtDelta], ["누적 소득 차이", comparison.cumulativeIncomeDelta],
    ["누적 소비 차이", comparison.cumulativeConsumptionDelta],
  ];
  return <div className="comparison-summary-grid">{cards.map(([label, value]) => <article key={label}><span>{label}</span><strong className={label.includes("차이") ? signedClass(value) : ""}>{formatWon(value)}</strong></article>)}</div>;
}

function ComparisonChart({ viewModel }) {
  const [metric, setMetric] = useState("netWorth");
  const chartData = viewModel.monthlyRows.map((row) => ({
    yearMonth: row.yearMonth,
    baseline: chartNumber(row.baseline?.[metric]), baselineExact: row.baseline?.[metric],
    whatIf: chartNumber(row.whatIf?.[metric]), whatIfExact: row.whatIf?.[metric],
  }));
  return (
    <section className="whatif-result-section" aria-labelledby="comparison-chart-title">
      <div className="whatif-result-heading"><div><p className="eyebrow">BASELINE VS WHAT-IF</p><h2 id="comparison-chart-title">월별 비교</h2></div><label>비교 지표<select value={metric} onChange={(event) => setMetric(event.target.value)}>{Object.entries(METRICS).map(([key, item]) => <option value={key} key={key}>{item.label}</option>)}</select></label></div>
      <div className="comparison-chart" role="img" aria-label={`Baseline과 What-if의 월별 ${METRICS[metric].label} 비교 그래프`}>
        <ResponsiveContainer width="100%" height={360}>
          <LineChart data={chartData} margin={{ top: 10, right: 12, left: 4, bottom: 4 }}>
            <CartesianGrid stroke="#e0e7df" strokeDasharray="3 5" vertical={false} />
            <XAxis dataKey="yearMonth" tickFormatter={yearMonth} minTickGap={28} tick={{ fontSize: 11 }} />
            <YAxis tickFormatter={compactWon} width={72} tick={{ fontSize: 11 }} />
            <Tooltip content={<ComparisonTooltip metric={metric} />} />
            <Legend />
            <Line name="Baseline" type="monotone" dataKey="baseline" stroke="#718078" strokeWidth={2.5} strokeDasharray="7 6" dot={false} />
            <Line name="What-if" type="monotone" dataKey="whatIf" stroke="#173f32" strokeWidth={3} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}

function Checkpoints({ rows }) {
  return (
    <section className="whatif-result-section" aria-labelledby="whatif-checkpoint-title">
      <div className="whatif-result-heading"><div><p className="eyebrow">CHECKPOINTS</p><h2 id="whatif-checkpoint-title">1·3·5년 비교</h2></div><p>차이 = What-if − Baseline</p></div>
      <div className="checkpoint-comparison-wrap"><table className="checkpoint-comparison-table"><caption>Checkpoint별 Baseline과 What-if 비교</caption><thead><tr><th>시점</th><th>지표</th><th>Baseline</th><th>What-if</th><th>차이</th></tr></thead><tbody>
        {rows.flatMap((row) => CHECKPOINT_METRICS.map(([field, deltaField, label], index) => (
          <tr key={`${row.monthNumber}-${field}`}>{index === 0 && <th rowSpan={CHECKPOINT_METRICS.length}>{row.monthNumber / 12}년<br /><small>{yearMonth(row.yearMonth)}</small></th>}<th>{label}</th><td>{formatWon(row.baseline?.[field])}</td><td>{formatWon(row.whatIf?.[field])}</td><td className={signedClass(row.delta?.[deltaField])}>{formatWon(row.delta?.[deltaField])}</td></tr>
        ))) }
      </tbody></table></div>
    </section>
  );
}

function ImpactSummary({ impact }) {
  const available = IMPACT_FIELDS.filter(([field]) => impact?.[field] != null);
  if (!available.length) return null;
  return (
    <section className="whatif-result-section" aria-labelledby="impact-title">
      <div className="whatif-result-heading"><div><p className="eyebrow">IMPACT SUMMARY</p><h2 id="impact-title">영향 원인 분해</h2></div><p>Backend가 반환한 항목이며 합산해 순자산을 재계산하지 않습니다.</p></div>
      <div className="impact-grid">{available.map(([field, label]) => <article key={field} className={field === "residualDelta" ? "impact-residual" : ""}><span>{label}</span><strong className={signedClass(impact[field])}>{formatWon(impact[field])}</strong>{field === "residualDelta" && <p>예금이자, 복리, 현금 제약, 월별 반올림 등 직접 항목에 완전히 배분되지 않은 차이입니다.</p>}</article>)}</div>
    </section>
  );
}

function NormalizedEvents({ events, natural }) {
  return (
    <section className="whatif-result-section" aria-labelledby="normalized-events-title">
      <div className="whatif-result-heading"><div><p className="eyebrow">NORMALIZED EVENTS</p><h2 id="normalized-events-title">이벤트 해석 결과</h2></div><p>중요 조건을 다시 확인해주세요.</p></div>
      {events.length ? <div className="normalized-event-grid">{events.map((event) => <article key={event.eventId}><span>{EVENT_DEFINITIONS[event.eventType]?.label ?? event.eventType}</span><strong>{event.description}</strong><dl>{event.effectiveYearMonth && <div><dt>적용 월</dt><dd>{yearMonth(event.effectiveYearMonth)}</dd></div>}{event.startYearMonth && <div><dt>기간</dt><dd>{yearMonth(event.startYearMonth)} ~ {yearMonth(event.endYearMonth)} · 종료 월 포함</dd></div>}{event.amount != null && <div><dt>금액</dt><dd>{formatWon(event.amount)}</dd></div>}{event.monthlyDelta != null && <div><dt>월 증감액</dt><dd>{formatWon(event.monthlyDelta)}</dd></div>}</dl></article>)}</div> : <div className="contract-limit"><strong>{natural ? "현재 자연어 응답에는 정규화 Event 상세가 없습니다." : "정규화 이벤트가 없습니다."}</strong><p>{natural ? "조건의 출처나 값을 임의로 복원하지 않습니다. 중요한 조건은 입력 문장과 Preview에서 다시 확인해주세요." : "Backend 응답을 확인해주세요."}</p></div>}
    </section>
  );
}

function RiskAndExplanation({ viewModel }) {
  return (
    <div className="risk-explanation-grid">
      <section className="whatif-result-section"><p className="eyebrow">RISK</p><h2>위험 신호</h2>
        {viewModel.risks.length === 0 && viewModel.serviceWarnings.length === 0 ? <div className="risk-clear"><span>✓</span><div><strong>반환된 위험 신호 없음</strong><p>Backend 응답의 Risk와 Warning만 표시합니다.</p></div></div> : <ul className="whatif-risk-list">{viewModel.risks.map((risk, index) => <li key={`${risk.code}-${index}`}><span>{risk.severity}</span><strong>{RISK_LABELS[risk.code] ?? risk.code}</strong><p>{risk.summary}</p>{risk.affectedYearMonth && <small>{yearMonth(risk.affectedYearMonth)}</small>}</li>)}{viewModel.serviceWarnings.map((warning, index) => <li key={`warning-${index}`}><span>NOTICE</span><strong>서비스 경고</strong><p>{warning}</p></li>)}</ul>}
      </section>
      <section className="whatif-result-section"><p className="eyebrow">RULE-BASED EXPLANATION</p><h2>결과 설명</h2>
        {viewModel.explanation ? <div className="explanation-content"><strong>{viewModel.explanation.headline}</strong><p>{viewModel.explanation.summary}</p><dl>{viewModel.explanation.evidence.map((item) => <div key={item.fieldPath}><dt>{item.fieldPath}</dt><dd>{item.value}</dd></div>)}</dl><p>{viewModel.explanation.assumptionNotice}</p><small>{viewModel.explanation.disclaimer}</small></div> : <div className="contract-limit"><strong>직접 입력 결과에는 Explanation이 없습니다.</strong><p>새 설명을 AI로 생성하지 않고 영향 요약과 Backend 경고만 표시합니다.</p></div>}
      </section>
    </div>
  );
}

function MonthlyDetails({ rows }) {
  return (
    <details className="monthly-comparison-details"><summary>월별 상세 비교 <span>{rows.length}개월</span></summary><p>Backend가 월별 Delta를 제공하지 않아 해당 열은 재계산하지 않습니다.</p><div className="monthly-comparison-wrap"><table><caption>월별 Baseline과 What-if 금융 상태</caption><thead><tr><th>연월</th><th>Baseline 순자산</th><th>What-if 순자산</th><th>순자산 Delta</th><th>Baseline 유동자산</th><th>What-if 유동자산</th><th>Baseline 부채</th><th>What-if 부채</th><th>현금 부족</th><th>음의 상환</th></tr></thead><tbody>{rows.map((row) => <tr key={row.monthNumber}><th>{yearMonth(row.yearMonth)}</th><td>{formatWon(row.baseline.netWorth)}</td><td>{formatWon(row.whatIf?.netWorth)}</td><td>API 미제공</td><td>{formatWon(row.baseline.liquidAssets)}</td><td>{formatWon(row.whatIf?.liquidAssets)}</td><td>{formatWon(row.baseline.remainingDebt)}</td><td>{formatWon(row.whatIf?.remainingDebt)}</td><td>{row.whatIf?.cashShortfall ? "발생" : "없음"}</td><td>{row.whatIf?.negativeAmortization ? "발생" : "없음"}</td></tr>)}</tbody></table></div></details>
  );
}

function Metadata({ viewModel }) {
  const metadata = viewModel.metadata;
  return (
    <section className="whatif-result-section transparency-panel"><div><p className="eyebrow">PROCESS TRANSPARENCY</p><h2>처리 경계</h2></div><div className="metadata-chips"><span>AI 사용 {metadata.aiUsed ? "예" : "아니요"}</span>{metadata.provider && <span>Provider {metadata.provider}</span>}{metadata.model && <span>Model {metadata.model}</span>}{metadata.privacyMode && <span>Privacy {metadata.privacyMode}</span>}<span>금융값 토큰화 {metadata.financialValuesTokenized ? "예" : "해당 없음"}</span><span>Agent Tool Call {metadata.toolCallCount}</span></div><p><strong>AI 사용 범위:</strong> 자연어 시나리오 구조화 · <strong>AI 미사용 범위:</strong> 금융 계산, 위험 판정, 순자산 비교</p>{viewModel.trace.length > 0 && <details><summary>처리 단계</summary><ol>{viewModel.trace.map((step) => <li key={step.sequence}><span>{step.sequence}</span><strong>{step.state}</strong><small>{step.component} · {step.outcomeCode}</small></li>)}</ol></details>}</section>
  );
}

function CalculationBasis({ viewModel }) {
  const basis = viewModel.baseline?.calculationBasis;
  const items = [
    ["월 이율", basis?.monthlyRateFormula],
    ["금액 반올림", basis?.moneyRounding],
    ["저축 처리", basis?.savingsTreatment],
    ["투자 처리", basis?.investmentTreatment],
  ].filter(([, value]) => value);
  return (
    <section className="calculation-disclaimer">
      <strong>계산 기준과 면책</strong>
      {items.length > 0 && <dl className="calculation-basis-grid">{items.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>}
      <p>{viewModel.disclaimer || "사용자 가정과 구조화 이벤트에 기반한 결정론적 비교이며 예측이나 보장이 아닙니다."}</p>
      <small>{viewModel.privacyNotice}</small>
    </section>
  );
}

export function ScenarioComparisonResults({ viewModel }) {
  return (
    <div className="whatif-results" aria-live="polite">
      <section className="whatif-result-hero"><div><p className="eyebrow">SCENARIO COMPARISON · {viewModel.mode.toUpperCase()}</p><h2>Baseline과 What-if의 차이</h2><p>차이 = What-if − Baseline. 색상은 방향만 나타내며 좋고 나쁨을 단정하지 않습니다.</p></div>{viewModel.financialProfileVersion && <span>PROFILE V{viewModel.financialProfileVersion}</span>}</section>
      {viewModel.fullComparisonAvailable ? <DirectSummary viewModel={viewModel} /> : <NaturalSummary viewModel={viewModel} />}
      <NormalizedEvents events={viewModel.normalizedEvents} natural={viewModel.mode === "natural"} />
      {viewModel.fullComparisonAvailable ? <><ComparisonChart viewModel={viewModel} /><Checkpoints rows={viewModel.checkpoints} /></> : <section className="contract-limit contract-limit--wide"><strong>자연어 API는 월별 Series와 Checkpoint를 반환하지 않습니다.</strong><p>현재 계약에서 제공되지 않는 Chart와 이벤트 상세를 Frontend에서 추정하거나 재계산하지 않습니다. 전체 월별 비교는 직접 입력 방식에서 확인할 수 있습니다.</p></section>}
      <ImpactSummary impact={viewModel.impactSummary} />
      <RiskAndExplanation viewModel={viewModel} />
      {viewModel.fullComparisonAvailable && <MonthlyDetails rows={viewModel.monthlyRows} />}
      <Metadata viewModel={viewModel} />
      <CalculationBasis viewModel={viewModel} />
    </div>
  );
}
