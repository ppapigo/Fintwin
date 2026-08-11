import { formatWon } from "../../utils/money";

export const DRAFT_FIELD_MAP = Object.freeze([
  ["monthlyIncome", "월 소득"],
  ["monthlyFixedExpenses", "월 고정지출"],
  ["monthlyVariableExpenses", "월 변동지출"],
  ["monthlySavings", "월 저축액"],
  ["monthlyInvestment", "월 투자액"],
]);

const WARNING_MESSAGES = Object.freeze({
  INSUFFICIENT_HISTORY: "분석 기간이 짧아 패턴의 신뢰도가 낮을 수 있습니다.",
  NO_INCOME_FOUND: "소득 거래가 없어 월 소득 추정값을 확인해야 합니다.",
  NO_EXPENSE_FOUND: "지출 거래가 없어 지출 패턴을 추정하지 못했습니다.",
  HIGH_INCOME_VOLATILITY: "월별 소득 변동성이 높습니다.",
  HIGH_EXPENSE_VOLATILITY: "월별 지출 변동성이 높습니다.",
  NEGATIVE_CASH_FLOW_MONTHS: "현금흐름이 음수인 달이 포함되어 있습니다.",
  LOW_SAVINGS_RATE: "관측 기간의 저축률이 낮습니다.",
  RECURRING_PATTERN_UNAVAILABLE: "반복 거래를 판단하기에 충분한 이력이 없습니다.",
  MANY_UNCATEGORIZED_TRANSACTIONS: "미분류 거래 비중이 높아 결과를 검토해야 합니다.",
  PROFILE_REVIEW_REQUIRED: "Profile Draft는 자동 저장되지 않으며 사용자 검토가 필요합니다.",
});

function warningMessage(code) {
  return WARNING_MESSAGES[code] || "분석 과정에서 검토가 필요한 항목이 발견되었습니다.";
}

function percentage(value) {
  return `${String(value ?? "0")}%`;
}

export function PatternAnalysisResults({ result, selectedFields, onToggleField, onPrepareReview }) {
  const draft = result.profileDraft?.estimatedValues || {};
  const comparison = result.currentProfileComparison;
  const current = comparison?.currentValues || {};
  const deltas = comparison?.deltas || {};

  return (
    <section className="pattern-results" aria-labelledby="pattern-results-title">
      <div className="pattern-result-hero">
        <div>
          <p className="eyebrow">DETERMINISTIC PATTERN ANALYSIS</p>
          <h2 id="pattern-results-title">거래 패턴 분석 결과</h2>
          <p>
            {result.analysisPeriod?.startYearMonth}부터 {result.analysisPeriod?.endYearMonth}까지
            {" "}{result.transactionCount}개 거래를 메모리에서만 분석했습니다.
          </p>
        </div>
        <dl>
          <div><dt>월 평균 소득</dt><dd>{formatWon(result.averages?.monthlyIncome)}</dd></div>
          <div><dt>월 평균 지출</dt><dd>{formatWon(result.averages?.monthlyExpenses)}</dd></div>
          <div><dt>평균 잉여금</dt><dd>{formatWon(result.averages?.monthlySurplus)}</dd></div>
          <div><dt>저축률</dt><dd>{percentage(result.averages?.savingsRatePercent)}</dd></div>
        </dl>
      </div>

      {result.warnings?.length > 0 && (
        <section className="pattern-panel pattern-warning-panel" aria-labelledby="pattern-warning-title">
          <h3 id="pattern-warning-title">검토 경고</h3>
          <ul>
            {result.warnings.map((warning) => (
              <li key={warning.code}><strong>{warning.code}</strong><span>{warningMessage(warning.code)}</span></li>
            ))}
          </ul>
        </section>
      )}

      <section className="pattern-panel" aria-labelledby="draft-title">
        <div className="panel-heading">
          <div><p className="eyebrow">PROFILE DRAFT</p><h3 id="draft-title">현재 Profile과 분석값 비교</h3></div>
          {comparison && <span>현재 Snapshot v{comparison.financialProfileVersion}</span>}
        </div>
        {!comparison ? (
          <p className="empty-copy">현재 Profile이 없어 Draft를 저장할 수 없습니다. 먼저 Profile을 생성해 주세요.</p>
        ) : (
          <>
            <div className="pattern-table-wrap">
              <table className="pattern-table">
                <thead><tr><th scope="col">반영</th><th scope="col">항목</th><th scope="col">현재값</th><th scope="col">분석값</th><th scope="col">차이</th></tr></thead>
                <tbody>
                  {DRAFT_FIELD_MAP.map(([field, label]) => (
                    <tr key={field}>
                      <td>
                        <input
                          id={`apply-${field}`}
                          type="checkbox"
                          checked={selectedFields.includes(field)}
                          onChange={() => onToggleField(field)}
                          aria-label={`${label} 분석값 반영`}
                        />
                      </td>
                      <th scope="row"><label htmlFor={`apply-${field}`}>{label}</label></th>
                      <td>{formatWon(current[field])}</td>
                      <td>{formatWon(draft[field])}</td>
                      <td>{formatWon(deltas[field])}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="pattern-review-action">
              <p>선택한 값만 수정 폼에 복사되며, 이 단계에서는 Profile이 변경되지 않습니다.</p>
              <button
                className="button button--primary"
                type="button"
                disabled={selectedFields.length === 0}
                onClick={onPrepareReview}
              >
                선택값 수정 폼에서 검토
              </button>
            </div>
          </>
        )}
      </section>

      <div className="pattern-result-grid">
        <section className="pattern-panel" aria-labelledby="monthly-pattern-title">
          <div className="panel-heading"><div><p className="eyebrow">MONTHLY CASH FLOW</p><h3 id="monthly-pattern-title">월별 집계</h3></div></div>
          <div className="pattern-table-wrap pattern-table-wrap--scroll">
            <table className="pattern-table">
              <thead><tr><th scope="col">월</th><th scope="col">소득</th><th scope="col">지출</th><th scope="col">저축</th><th scope="col">투자</th><th scope="col">잉여금</th></tr></thead>
              <tbody>
                {result.monthlyCashFlows?.map((month) => (
                  <tr key={month.yearMonth}>
                    <th scope="row">{month.yearMonth}</th>
                    <td>{formatWon(month.income)}</td><td>{formatWon(month.expenses)}</td>
                    <td>{formatWon(month.savingTransfers)}</td><td>{formatWon(month.investmentTransfers)}</td>
                    <td>{formatWon(month.monthlySurplus)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="pattern-panel" aria-labelledby="category-pattern-title">
          <div className="panel-heading"><div><p className="eyebrow">CATEGORY MIX</p><h3 id="category-pattern-title">카테고리 지출 비율</h3></div></div>
          <ul className="pattern-category-list">
            {result.categorySpending?.map((category) => (
              <li key={category.category}>
                <span>{category.category}</span>
                <strong>{formatWon(category.totalExpenses)}</strong>
                <small>{percentage(category.spendingRatioPercent)}</small>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="pattern-panel" aria-labelledby="recurring-pattern-title">
        <div className="panel-heading"><div><p className="eyebrow">RECURRING SIGNALS</p><h3 id="recurring-pattern-title">반복 거래</h3></div></div>
        {result.recurringTransactions?.length > 0 ? (
          <ul className="recurring-list">
            {result.recurringTransactions.map((item, index) => (
              <li key={`${item.type}-${item.category}-${index}`}>
                <strong>{item.category}</strong><span>{item.type}</span>
                <span>월 평균 {formatWon(item.averageMonthlyAmount)}</span>
                <span>{item.detectedMonthCount}개월 관측</span>
              </li>
            ))}
          </ul>
        ) : <p className="empty-copy">탐지된 반복 거래가 없습니다.</p>}
      </section>

      <div className="privacy-callout">
        <strong>Privacy sealed</strong>
        <span>파일과 거래 원문은 저장하거나 외부 AI/API로 보내지 않으며, Draft는 승인 전까지 Profile에 반영되지 않습니다.</span>
      </div>
    </section>
  );
}
