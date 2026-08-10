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
import { addMoney, formatWon } from "../../utils/money";

const SERIES_LABELS = {
  totalFinancialAssets: "총 금융자산",
  remainingDebt: "남은 부채",
  netWorth: "순자산",
};

const CUMULATIVE_FIELDS = [
  ["income", "누적 소득"],
  ["consumption", "누적 소비"],
  ["savingsAllocated", "누적 저축 배정"],
  ["investmentContributions", "누적 투자 납입"],
  ["investmentReturn", "누적 투자손익"],
  ["debtInterest", "누적 대출이자"],
  ["principalRepaid", "누적 원금상환"],
];

function chartNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function compactWon(value) {
  return `${new Intl.NumberFormat("ko-KR", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(value)}원`;
}

function yearMonthLabel(value) {
  const [year, month] = String(value).split("-");
  return year && month ? `${year}.${month}` : value;
}

function ExactChartTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const source = payload[0]?.payload;
  return (
    <div className="chart-tooltip">
      <strong>{yearMonthLabel(label)} 기준</strong>
      {payload.map((item) => (
        <div key={item.dataKey}>
          <span style={{ color: item.color }}>{SERIES_LABELS[item.dataKey] ?? item.dataKey}</span>
          <b>{formatWon(source?.[`${item.dataKey}Exact`])}</b>
        </div>
      ))}
    </div>
  );
}

export function BaselineResults({ result }) {
  const lastMonth = result.monthlyResults.at(-1);
  const chartData = result.monthlyResults.map((item) => ({
    yearMonth: item.yearMonth,
    totalFinancialAssets: chartNumber(item.totalFinancialAssets),
    totalFinancialAssetsExact: item.totalFinancialAssets,
    remainingDebt: chartNumber(item.remainingDebt),
    remainingDebtExact: item.remainingDebt,
    netWorth: chartNumber(item.netWorth),
    netWorthExact: item.netWorth,
  }));
  const shortfallMonths = result.monthlyResults.filter((item) => item.cashShortfall).length;
  const negativeAmortizationMonths = result.monthlyResults.filter((item) => item.negativeAmortization).length;

  return (
    <div className="baseline-results" aria-live="polite">
      <section className="twin-result-hero">
        <div>
          <p className="eyebrow">MY FINANCIAL TWIN · PROFILE V{result.financialProfileVersion}</p>
          <h2>{result.horizonMonths}개월 기준선이 완성되었습니다</h2>
          <p>{result.startYearMonth}부터 사용자 가정만 적용한 결정론적 결과입니다. 결과는 저장되지 않습니다.</p>
        </div>
        <div className="result-assumptions" aria-label="적용된 가정">
          <span>소득 {result.assumptions.annualIncomeGrowthRate}%</span>
          <span>물가 {result.assumptions.annualInflationRate}%</span>
          <span>예금 {result.assumptions.annualDepositInterestRate}%</span>
          <span>투자 {result.assumptions.annualInvestmentReturnRate}%</span>
          <span>상환 {formatWon(result.assumptions.monthlyDebtPayment)}/월</span>
        </div>
      </section>

      {lastMonth && (
        <section className="twin-final-grid" aria-label="최종 월 결과">
          <article><span>최종 금융자산</span><strong>{formatWon(lastMonth.totalFinancialAssets)}</strong></article>
          <article><span>최종 남은 부채</span><strong>{formatWon(lastMonth.remainingDebt)}</strong></article>
          <article className="twin-final-card--accent"><span>최종 순자산</span><strong>{formatWon(lastMonth.netWorth)}</strong></article>
          <article><span>최종 월 가용현금</span><strong>{formatWon(lastMonth.disposableCashFlow)}</strong></article>
        </section>
      )}

      <section className="twin-section" aria-labelledby="checkpoint-title">
        <div className="twin-section-heading">
          <div><p className="eyebrow">CHECKPOINTS</p><h2 id="checkpoint-title">1·3·5년의 상태</h2></div>
          <p>선택한 기간 안에서 도달하는 시점만 표시합니다.</p>
        </div>
        <div className="checkpoint-grid">
          {result.checkpoints.map((checkpoint) => (
            <article className="checkpoint-card" key={checkpoint.monthNumber}>
              <div><strong>{checkpoint.monthNumber / 12}년 후</strong><time>{yearMonthLabel(checkpoint.yearMonth)}</time></div>
              <dl>
                <div><dt>순자산</dt><dd>{formatWon(checkpoint.netWorth)}</dd></div>
                <div><dt>금융자산</dt><dd>{formatWon(checkpoint.totalFinancialAssets)}</dd></div>
                <div><dt>남은 부채</dt><dd>{formatWon(checkpoint.remainingDebt)}</dd></div>
              </dl>
            </article>
          ))}
        </div>
      </section>

      <section className="twin-section twin-chart-section" aria-labelledby="asset-chart-title">
        <div className="twin-section-heading">
          <div><p className="eyebrow">STATE TRAJECTORY</p><h2 id="asset-chart-title">자산·부채·순자산</h2></div>
          <p>정확한 금액은 아래 월별 표에서 확인할 수 있습니다.</p>
        </div>
        <div className="twin-chart" role="img" aria-label="월별 총 금융자산, 남은 부채, 순자산 선 그래프">
          <ResponsiveContainer width="100%" height={340}>
            <LineChart data={chartData} margin={{ top: 10, right: 12, left: 4, bottom: 4 }}>
              <CartesianGrid stroke="#e0e7df" strokeDasharray="3 5" vertical={false} />
              <XAxis dataKey="yearMonth" tickFormatter={yearMonthLabel} minTickGap={28} tick={{ fontSize: 11 }} />
              <YAxis tickFormatter={compactWon} width={72} tick={{ fontSize: 11 }} />
              <Tooltip content={<ExactChartTooltip />} />
              <Legend formatter={(value) => SERIES_LABELS[value] ?? value} />
              <Line type="monotone" dataKey="totalFinancialAssets" stroke="#628f32" strokeWidth={3} dot={false} activeDot={{ r: 4 }} />
              <Line type="monotone" dataKey="remainingDebt" stroke="#b05c4d" strokeWidth={2.5} dot={false} activeDot={{ r: 4 }} />
              <Line type="monotone" dataKey="netWorth" stroke="#173f32" strokeWidth={3} dot={false} activeDot={{ r: 4 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="twin-section" aria-labelledby="cashflow-title">
        <div className="twin-section-heading">
          <div><p className="eyebrow">MONTHLY CASH FLOW</p><h2 id="cashflow-title">월별 현금흐름</h2></div>
          <p>{result.monthlyResults.length}개월 전체 결과</p>
        </div>
        <div className="cashflow-table-wrap">
          <table className="cashflow-table">
            <thead><tr><th scope="col">월</th><th scope="col">소득</th><th scope="col">소비</th><th scope="col">대출상환</th><th scope="col">저축</th><th scope="col">투자</th><th scope="col">가용현금</th><th scope="col">상태</th></tr></thead>
            <tbody>
              {result.monthlyResults.map((month) => {
                const consumption = addMoney(month.fixedExpenses, month.variableExpenses, month.oneTimeExpense);
                return (
                  <tr key={month.monthNumber}>
                    <th scope="row"><span>{month.monthNumber}개월</span><small>{yearMonthLabel(month.yearMonth)}</small></th>
                    <td>{formatWon(month.income)}</td>
                    <td>{formatWon(consumption)}</td>
                    <td>{formatWon(month.debtPayment)}</td>
                    <td>{formatWon(month.savingsAllocation)}</td>
                    <td>{formatWon(month.investmentContribution)}</td>
                    <td className={month.disposableCashFlow.startsWith("-") ? "money-negative" : ""}>{formatWon(month.disposableCashFlow)}</td>
                    <td>{month.cashShortfall ? <span className="risk-tag">현금 부족</span> : month.negativeAmortization ? <span className="risk-tag">부채 증가</span> : <span className="safe-tag">정상</span>}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </section>

      <section className="twin-section" aria-labelledby="cumulative-title">
        <div className="twin-section-heading">
          <div><p className="eyebrow">CUMULATIVE TOTALS</p><h2 id="cumulative-title">누적 결과</h2></div>
          <p>내부 이전과 수익을 분리한 엔진 집계입니다.</p>
        </div>
        <div className="cumulative-grid">
          {CUMULATIVE_FIELDS.map(([field, label]) => (
            <article key={field}><span>{label}</span><strong>{formatWon(result.finalCumulativeTotals[field])}</strong></article>
          ))}
        </div>
      </section>

      <div className="twin-bottom-grid">
        <section className="twin-section risk-panel" aria-labelledby="risk-title">
          <p className="eyebrow">RISK CHECK</p><h2 id="risk-title">엔진 위험 신호</h2>
          {shortfallMonths === 0 && negativeAmortizationMonths === 0 ? (
            <div className="risk-clear"><span aria-hidden="true">✓</span><div><strong>기준 경고 없음</strong><p>선택한 기간에 현금 부족과 부채 증가 플래그가 없습니다.</p></div></div>
          ) : (
            <ul className="risk-list">
              {shortfallMonths > 0 && <li><strong>현금 부족 {shortfallMonths}개월</strong><span>의무지출 후 유동자산이 부족한 달입니다.</span></li>}
              {negativeAmortizationMonths > 0 && <li><strong>부채 증가 {negativeAmortizationMonths}개월</strong><span>상환액이 해당 월 이자보다 적은 달입니다.</span></li>}
            </ul>
          )}
        </section>

        <section className="twin-section basis-panel" aria-labelledby="basis-title">
          <p className="eyebrow">CALCULATION BASIS</p><h2 id="basis-title">계산 기준</h2>
          <dl>
            <div><dt>월 이율</dt><dd>{result.calculationBasis.monthlyRateFormula}</dd></div>
            <div><dt>금액 반올림</dt><dd>{result.calculationBasis.moneyRounding}</dd></div>
            <div><dt>저축 처리</dt><dd>{result.calculationBasis.savingsTreatment}</dd></div>
            <div><dt>투자 처리</dt><dd>{result.calculationBasis.investmentTreatment}</dd></div>
          </dl>
          <p className="basis-disclaimer">{result.calculationBasis.disclaimer}</p>
        </section>
      </div>
    </div>
  );
}
