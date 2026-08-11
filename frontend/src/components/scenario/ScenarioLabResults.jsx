import { useMemo, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { formatWon } from "../../utils/money";

const COLORS = Object.freeze({ A: "#6f7f77", B: "#27644f", C: "#8cba39", D: "#c17a46", E: "#75629a" });
const METRICS = Object.freeze({
  netWorth: "순자산",
  liquidAssets: "유동자산",
  investmentAssets: "투자자산",
  remainingDebt: "부채",
  disposableCashFlow: "월 현금흐름",
});
const WARNING_MESSAGES = Object.freeze({
  CASH_SHORTFALL: "가정 기간 중 현금 부족이 발생합니다.",
  NEGATIVE_AMORTIZATION: "일부 월에 납입액보다 이자가 커서 부채가 충분히 줄지 않습니다.",
  NET_WORTH_BELOW_BASELINE: "최종 순자산이 기준안보다 낮습니다.",
  LIQUID_ASSETS_BELOW_BASELINE: "최종 유동자산이 기준안보다 낮습니다.",
  DEBT_ABOVE_BASELINE: "최종 부채가 기준안보다 높습니다.",
  INVESTMENT_CONTRIBUTION_CASH_LIMITED: "투자 납입액 일부가 가용 현금 범위로 제한됐습니다.",
  EXTRA_DEBT_REPAYMENT_LIMITED: "추가 상환액 일부가 잔여 부채 또는 가용 현금 범위로 제한됐습니다.",
  EVENT_PERIOD_CLIPPED: "일부 이벤트 기간이 시뮬레이션 범위에 맞게 잘렸습니다.",
});

function warningText(code) {
  return WARNING_MESSAGES[code] ?? "계산 경고가 있습니다. 입력 가정과 해당 Scenario 결과를 확인해주세요.";
}

function deltaText(value) {
  if (value == null) return "기준안";
  const text = String(value);
  return `${text.startsWith("-") ? "" : "+"}${formatWon(text)}`;
}

function ChartTooltip({ active, payload, label, series }) {
  if (!active || !payload?.length) return null;
  const row = payload[0].payload;
  return (
    <div className="chart-tooltip scenario-lab-tooltip">
      <strong>{label}</strong>
      {series.map((item) => row.raw[item.key] != null && (
        <div key={item.key}><span style={{ color: item.color }}>{item.label}</span><b>{formatWon(row.raw[item.key])}</b></div>
      ))}
    </div>
  );
}

function ScenarioCard({ scenario, baseline = false }) {
  const warnings = scenario.warnings ?? [];
  return (
    <article className="scenario-result-card" style={{ "--scenario-color": COLORS[scenario.scenarioKey] }}>
      <div className="scenario-result-card__heading"><span>{scenario.scenarioKey}</span><div><small>{baseline ? "BASELINE" : "SCENARIO"}</small><h3>{scenario.label}</h3></div></div>
      <dl>
        <div><dt>최종 순자산</dt><dd>{formatWon(scenario.finalNetWorth)}</dd></div>
        <div><dt>Baseline 대비</dt><dd>{deltaText(scenario.baselineDelta?.netWorthDelta)}</dd></div>
        <div><dt>유동자산</dt><dd>{formatWon(scenario.finalLiquidAssets)}</dd></div>
        <div><dt>투자자산</dt><dd>{formatWon(scenario.finalInvestmentAssets)}</dd></div>
        <div><dt>부채</dt><dd>{formatWon(scenario.finalDebt)}</dd></div>
        <div><dt>마지막 달 현금흐름</dt><dd>{formatWon(scenario.lastMonthDisposableCashFlow)}</dd></div>
      </dl>
      <p><strong>적용 Event {scenario.normalizedEvents?.length ?? 0}개</strong><span>{warnings.length ? ` · Risk ${warnings.length}건` : " · 주요 Risk 없음"}</span></p>
    </article>
  );
}

export function ScenarioLabResults({ result }) {
  const [metric, setMetric] = useState("netWorth");
  const [selectedKeys, setSelectedKeys] = useState(() => result.scenarios.map((scenario) => scenario.scenarioKey));
  const baseline = { scenarioKey: "A", label: "현재 생활 유지", normalizedEvents: [], warnings: [], ...result.baseline };
  const columns = [baseline, ...result.scenarios];
  const selectedScenarios = result.scenarios.filter((scenario) => selectedKeys.includes(scenario.scenarioKey));
  const series = [{ key: "A", label: baseline.label, color: COLORS.A },
    ...selectedScenarios.map((scenario) => ({ key: scenario.scenarioKey, label: scenario.label, color: COLORS[scenario.scenarioKey] }))];
  const chartData = useMemo(() => {
    const scenarioMaps = new Map(result.scenarios.map((scenario) => [scenario.scenarioKey,
      new Map(scenario.monthlyResults.map((month) => [month.yearMonth, month]))]));
    return result.baseline.monthlyResults.map((month) => {
      const row = { yearMonth: month.yearMonth, raw: { A: month[metric] }, A: Number(month[metric]) };
      for (const scenario of selectedScenarios) {
        const scenarioMonth = scenarioMaps.get(scenario.scenarioKey)?.get(month.yearMonth);
        if (scenarioMonth?.[metric] != null) {
          row.raw[scenario.scenarioKey] = scenarioMonth[metric];
          row[scenario.scenarioKey] = Number(scenarioMonth[metric]);
        }
      }
      return row;
    });
  }, [metric, result, selectedKeys]);

  const rows = [
    ["최종 순자산", (item) => item.finalNetWorth],
    ["유동자산", (item) => item.finalLiquidAssets],
    ["투자자산", (item) => item.finalInvestmentAssets],
    ["총 금융자산", (item) => item.finalTotalFinancialAssets],
    ["부채", (item) => item.finalDebt],
    ["마지막 달 가처분 현금흐름", (item) => item.lastMonthDisposableCashFlow],
    ["누적 소득", (item) => item.finalCumulativeTotals.income],
    ["누적 소비", (item) => item.finalCumulativeTotals.consumption],
    ["누적 투자", (item) => item.finalCumulativeTotals.investmentContributions],
    ["누적 대출이자", (item) => item.finalCumulativeTotals.debtInterest],
    ["누적 원금상환", (item) => item.finalCumulativeTotals.principalRepaid],
  ];

  function toggleScenario(key) {
    setSelectedKeys((current) => current.includes(key)
      ? current.filter((item) => item !== key)
      : [...current, key]);
  }

  return (
    <section className="scenario-lab-results" aria-labelledby="scenario-results-title">
      <header className="scenario-results-hero">
        <div><p className="eyebrow">SCENARIO COMPARISON</p><h2 id="scenario-results-title">같은 출발점, 다른 선택</h2><p>Profile v{result.financialProfileVersion} · {result.horizonMonths}개월 · 결과 저장 없음</p></div>
        <span>DELTA = SCENARIO − BASELINE</span>
      </header>

      <div className="scenario-result-cards">
        <ScenarioCard scenario={baseline} baseline />
        {result.scenarios.map((scenario) => <ScenarioCard key={scenario.scenarioKey} scenario={scenario} />)}
      </div>

      <section className="scenario-result-panel" aria-labelledby="scenario-table-title">
        <div className="whatif-result-heading"><div><p className="eyebrow">COMPARISON TABLE</p><h2 id="scenario-table-title">Backend 결과 비교</h2></div><p>Scenario 간 값이나 순위를 Frontend에서 새로 계산하지 않습니다.</p></div>
        <div className="scenario-table-wrap"><table><caption>Baseline과 Scenario 최종 지표 비교</caption><thead><tr><th scope="col">지표</th>{columns.map((item) => <th scope="col" key={item.scenarioKey}><span style={{ color: COLORS[item.scenarioKey] }}>{item.scenarioKey}</span> {item.label}</th>)}</tr></thead><tbody>{rows.map(([label, value]) => <tr key={label}><th scope="row">{label}</th>{columns.map((item) => <td key={item.scenarioKey}>{formatWon(value(item))}</td>)}</tr>)}</tbody></table></div>
      </section>

      <section className="scenario-result-panel" aria-labelledby="scenario-chart-title">
        <div className="scenario-chart-controls"><div><p className="eyebrow">MONTHLY SERIES</p><h2 id="scenario-chart-title">월별 경로 비교</h2></div><label>Metric<select aria-label="Chart Metric" value={metric} onChange={(event) => setMetric(event.target.value)}>{Object.entries(METRICS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label></div>
        <div className="scenario-desktop-selectors" aria-label="표시할 Scenario">{result.scenarios.map((scenario) => <label key={scenario.scenarioKey}><input type="checkbox" checked={selectedKeys.includes(scenario.scenarioKey)} onChange={() => toggleScenario(scenario.scenarioKey)} /><span style={{ "--scenario-color": COLORS[scenario.scenarioKey] }}>{scenario.scenarioKey}</span>{scenario.label}</label>)}</div>
        <label className="scenario-mobile-selector">모바일 표시 Scenario<select value={selectedKeys[0] ?? ""} onChange={(event) => setSelectedKeys(event.target.value ? [event.target.value] : [])}><option value="">Baseline만</option>{result.scenarios.map((scenario) => <option key={scenario.scenarioKey} value={scenario.scenarioKey}>{scenario.scenarioKey}. {scenario.label}</option>)}</select></label>
        <div className="scenario-chart"><ResponsiveContainer width="100%" height={380}><LineChart data={chartData} margin={{ top: 12, right: 16, bottom: 8, left: 4 }}><CartesianGrid strokeDasharray="3 5" vertical={false} /><XAxis dataKey="yearMonth" minTickGap={24} tick={{ fontSize: 10 }} /><YAxis width={84} tick={{ fontSize: 10 }} /><Tooltip content={<ChartTooltip series={series} />} />{series.map((item) => <Line key={item.key} type="monotone" dataKey={item.key} name={item.label} stroke={item.color} strokeWidth={item.key === "A" ? 2 : 2.5} strokeDasharray={item.key === "A" ? "5 5" : undefined} dot={false} connectNulls={false} />)}</LineChart></ResponsiveContainer></div>
        <p className="goal-chart-note">좌표 렌더링에만 Number 변환을 사용하며 Tooltip은 Backend 금액 문자열을 표시합니다. 누락된 월은 연결하거나 보간하지 않습니다.</p>
      </section>

      <section className="scenario-result-panel" aria-labelledby="checkpoint-title">
        <div className="whatif-result-heading"><div><p className="eyebrow">CHECKPOINTS</p><h2 id="checkpoint-title">1·3·5년 Checkpoint</h2></div><p>Backend가 반환한 Horizon 내 Checkpoint만 표시합니다.</p></div>
        <div className="checkpoint-lab-grid">{result.checkpointComparisons.map((checkpoint) => <article key={checkpoint.monthNumber}><strong>{checkpoint.monthNumber / 12}년 · {checkpoint.yearMonth}</strong><dl><div><dt>A 순자산</dt><dd>{formatWon(checkpoint.baseline.netWorth)}</dd></div>{checkpoint.scenarios.map((scenario) => <div key={scenario.scenarioKey}><dt>{scenario.scenarioKey} 순자산</dt><dd>{formatWon(scenario.result.netWorth)} <small>{deltaText(scenario.baselineDelta?.netWorthDelta)}</small></dd></div>)}</dl></article>)}</div>
      </section>

      <section className="scenario-result-panel" aria-labelledby="risk-title">
        <div className="whatif-result-heading"><div><p className="eyebrow">RISK CHECK</p><h2 id="risk-title">위험과 계산 경고</h2></div><p>기존 월별 결과와 Event 적용 결과만 사용합니다.</p></div>
        {result.calculationWarnings.length ? <ul className="scenario-warning-list">{result.calculationWarnings.map((warning, index) => <li key={`${warning.scope}-${warning.scenarioKey}-${warning.code}-${index}`}><strong>{warning.scenarioKey ?? "A"} · {warning.code}</strong><p>{warningText(warning.code)}{warning.affectedYearMonth ? ` 최초 확인 월 ${warning.affectedYearMonth}` : ""}</p></li>)}</ul> : <p className="scenario-no-warning">구조화된 계산 경고가 없습니다. 이는 미래 결과를 보장한다는 뜻이 아닙니다.</p>}
      </section>

      <section className="calculation-disclaimer"><p className="eyebrow">CALCULATION BASIS</p><h2>결정론적 계산 기준과 면책</h2><dl><div><dt>월 이율</dt><dd>{result.calculationBasis.monthlyRateFormula}</dd></div><div><dt>금액 반올림</dt><dd>{result.calculationBasis.moneyRounding}</dd></div><div><dt>저축 처리</dt><dd>{result.calculationBasis.savingsTreatment}</dd></div><div><dt>투자 처리</dt><dd>{result.calculationBasis.investmentTreatment}</dd></div></dl><p>{result.disclaimer} 세금·수수료·실제 수익과 소득 변동은 다를 수 있으며, 이 결과는 금융상품 추천이나 투자 자문이 아닙니다.</p></section>
    </section>
  );
}
