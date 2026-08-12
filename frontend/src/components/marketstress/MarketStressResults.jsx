import { useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatWon } from "../../utils/money";

const METRICS = {
  netWorth: "순자산",
  investmentAssets: "투자자산",
  liquidAssets: "유동자산",
  remainingDebt: "부채",
};

const WARNING_MESSAGES = {
  INVESTMENT_ASSET_LOSS: "입력한 시장·환율 충격으로 투자자산 가치가 감소합니다.",
  LOAN_INTEREST_INCREASE: "입력한 금리 변화로 누적 대출이자가 증가합니다.",
  CASH_SHORTFALL: "Stress 결과에 현금 부족 월이 포함됩니다.",
  NEGATIVE_AMORTIZATION: "Stress 결과에 원금이 줄지 않는 음의 상환 월이 포함됩니다.",
  GOAL_MARGIN_REDUCED: "Stress 결과에서 목표 순자산 Margin이 감소합니다.",
  MARKET_DATA_STALE: "일부 시장 관측값이 최신 기준을 벗어났습니다.",
  MARKET_DATA_UNAVAILABLE: "일부 공식 시장 관측값을 확인할 수 없습니다.",
};

function chartNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function compactWon(value) {
  return `${new Intl.NumberFormat("ko-KR", { notation: "compact", maximumFractionDigits: 1 }).format(value)}원`;
}

function ExactTooltip({ active, payload, label, metric }) {
  if (!active || !payload?.length) return null;
  const source = payload[0]?.payload;
  return (
    <div className="chart-tooltip">
      <strong>{label}</strong>
      <div><span style={{ color: "#718078" }}>Baseline</span><b>{formatWon(source?.baselineExact)}</b></div>
      <div><span style={{ color: "#b64d42" }}>Stress</span><b>{formatWon(source?.stressedExact)}</b></div>
      <small>{METRICS[metric]}</small>
    </div>
  );
}

export function MarketStressResults({ result }) {
  const [metric, setMetric] = useState("netWorth");
  const baselineLast = result.baseline.monthlyResults.at(-1);
  const stressedLast = result.stressed.monthlyResults.at(-1);
  const chartData = result.baseline.monthlyResults.map((baseline, index) => {
    const stressed = result.stressed.monthlyResults[index] ?? {};
    return {
      yearMonth: baseline.yearMonth,
      baseline: chartNumber(baseline[metric]),
      baselineExact: baseline[metric],
      stressed: chartNumber(stressed[metric]),
      stressedExact: stressed[metric],
    };
  });
  const impact = result.marketImpactBreakdown;
  const risk = result.riskComparison;
  const goal = result.goalMarginComparison;

  return (
    <div className="market-stress-results" aria-live="polite">
      <section className="market-stress-result-hero">
        <div><p className="eyebrow">BASELINE VS STRESS · PROFILE V{result.financialProfileVersion}</p><h2>{result.horizonMonths}개월 충격 비교</h2><p>{impact.shockYearMonth}에 입력한 충격을 한 번 적용한 결정론적 결과입니다.</p></div>
        <span>OBSERVATIONS NOT USED</span>
      </section>

      <section className="market-stress-summary-grid" aria-label="최종 비교">
        <article><span>Baseline 최종 순자산</span><strong>{formatWon(baselineLast?.netWorth)}</strong></article>
        <article><span>Stress 최종 순자산</span><strong>{formatWon(stressedLast?.netWorth)}</strong></article>
        <article className="market-stress-negative"><span>최종 순자산 차이</span><strong>{formatWon(impact.finalNetWorthDelta)}</strong></article>
        <article><span>추가 누적 대출이자</span><strong>{formatWon(impact.additionalDebtInterest)}</strong></article>
      </section>

      <section className="market-stress-panel" aria-labelledby="impact-breakdown-title">
        <div className="market-stress-section-heading"><div><p className="eyebrow">MARKET IMPACT BREAKDOWN</p><h2 id="impact-breakdown-title">충격별 영향</h2></div><p>해외 주식 충격 후 잔액에 환율 충격을 순차 적용합니다.</p></div>
        <div className="market-impact-grid">
          <article><span>국내 주식 충격</span><strong>{formatWon(impact.domesticStockImpact)}</strong><small>충격 직전 Exposure {formatWon(impact.domesticExposureAtShock)}</small></article>
          <article><span>해외 주식 충격</span><strong>{formatWon(impact.overseasStockImpact)}</strong><small>충격 직전 Exposure {formatWon(impact.overseasExposureAtShock)}</small></article>
          <article><span>환율 영향</span><strong>{formatWon(impact.exchangeRateImpact)}</strong><small>해외 주식 충격 후 잔액 기준</small></article>
          <article><span>총 투자자산 영향</span><strong>{formatWon(impact.totalInvestmentImpact)}</strong><small>실제 시장 예측값이 아님</small></article>
        </div>
      </section>

      <section className="market-stress-panel" aria-labelledby="stress-chart-title">
        <div className="market-stress-chart-heading"><div><p className="eyebrow">MONTHLY COMPARISON</p><h2 id="stress-chart-title">월별 경로</h2></div><label htmlFor="marketStressMetric">지표<select id="marketStressMetric" value={metric} onChange={(event) => setMetric(event.target.value)}>{Object.entries(METRICS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
        <div className="market-stress-chart">
          <ResponsiveContainer width="100%" height={380}>
            <LineChart data={chartData} margin={{ top: 15, right: 18, left: 8, bottom: 8 }}>
              <CartesianGrid strokeDasharray="3 4" stroke="#d9e1dc" />
              <XAxis dataKey="yearMonth" minTickGap={32} tick={{ fontSize: 11 }} />
              <YAxis tickFormatter={compactWon} width={78} tick={{ fontSize: 11 }} />
              <Tooltip content={<ExactTooltip metric={metric} />} />
              <Legend />
              <Line type="monotone" dataKey="baseline" name="Baseline" stroke="#718078" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="stressed" name="Stress" stroke="#b64d42" strokeWidth={2.5} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="market-stress-two-column">
        <article className="market-stress-panel"><p className="eyebrow">RISK COMPARISON</p><h2>위험 플래그</h2><dl className="market-risk-list"><div><dt>현금 부족 월</dt><dd>Baseline {risk.baseline.cashShortfallMonthCount} → Stress {risk.stressed.cashShortfallMonthCount}</dd></div><div><dt>음의 상환 월</dt><dd>Baseline {risk.baseline.negativeAmortizationMonthCount} → Stress {risk.stressed.negativeAmortizationMonthCount}</dd></div><div><dt>Stress 최소 유동자산</dt><dd>{formatWon(risk.stressed.minimumLiquidAssets)}</dd></div><div><dt>Stress 최종 부채</dt><dd>{formatWon(risk.stressed.finalRemainingDebt)}</dd></div></dl></article>
        <article className="market-stress-panel"><p className="eyebrow">GOAL MARGIN</p><h2>목표 영향</h2>{goal.status === "NOT_PROVIDED" ? <p className="market-stress-muted">목표 순자산을 입력하지 않아 최종 순자산만 비교했습니다.</p> : <dl className="market-risk-list"><div><dt>목표 순자산</dt><dd>{formatWon(goal.targetNetWorth)}</dd></div><div><dt>Baseline Margin</dt><dd>{formatWon(goal.baselineMargin)}</dd></div><div><dt>Stress Margin</dt><dd>{formatWon(goal.stressedMargin)}</dd></div><div><dt>Margin 변화</dt><dd>{formatWon(goal.marginDelta)}</dd></div></dl>}</article>
      </section>

      <section className="market-stress-warning-panel" aria-labelledby="stress-warning-title"><p className="eyebrow">WARNINGS</p><h2 id="stress-warning-title">확인해야 할 위험</h2>{result.warnings.length ? <ul>{result.warnings.map((warning, index) => <li key={`${warning.code}-${index}`}><strong>{warning.code}</strong><p>{WARNING_MESSAGES[warning.code] ?? "입력한 가정 아래 추가 위험이 확인되었습니다. 계산 기준과 월별 결과를 함께 검토해주세요."}</p></li>)}</ul> : <p>추가 구조화 경고가 없습니다. 이는 안전이나 수익을 보장한다는 의미가 아닙니다.</p>}</section>

      <section className="market-stress-disclaimer"><strong>계산 경계와 면책</strong><p>현재 시장 관측값은 계산에 사용하지 않습니다. 투자수익률과 모든 충격은 사용자가 입력한 가정이며 세금·수수료·실제 시장가격을 반영하지 않을 수 있습니다. 결과는 생성형 AI가 아닌 결정론적 금융 엔진이 계산하며 금융상품 추천, 투자자문, 미래 예측 또는 손실 한도 보장이 아닙니다.</p></section>
    </div>
  );
}
