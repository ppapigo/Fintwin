const LABELS = {
  KOSPI_INDEX: ["KOSPI", "지수"],
  KRW_USD_EXCHANGE_RATE: ["원/달러 환율", "원/USD"],
  BOK_BASE_RATE: ["한국은행 기준금리", "%"],
};

const STATUS_LABELS = {
  AVAILABLE: "확인 가능",
  STALE: "오래된 관측값",
  UNAVAILABLE: "확인 불가",
};

export function MarketContextPanel({ context }) {
  const observations = context?.observations ?? [];
  return (
    <section className="market-context-panel" aria-labelledby="market-context-title">
      <div className="market-stress-section-heading">
        <div><p className="eyebrow">OFFICIAL MARKET CONTEXT</p><h2 id="market-context-title">현재 관측값</h2></div>
        <span className={`market-data-status market-data-status--${String(context?.status ?? "UNAVAILABLE").toLowerCase()}`}>
          {context?.status === "AVAILABLE" ? "전체 확인" : context?.status === "PARTIAL" ? "일부 확인" : "확인 불가"}
        </span>
      </div>
      <div className="market-context-grid">
        {["KOSPI_INDEX", "KRW_USD_EXCHANGE_RATE", "BOK_BASE_RATE"].map((indicator) => {
          const item = observations.find((entry) => entry.indicator === indicator);
          const [label, fallbackUnit] = LABELS[indicator];
          return (
            <article key={indicator}>
              <div><span>{label}</span><small>{STATUS_LABELS[item?.status] ?? "확인 불가"}</small></div>
              <strong>{item?.value == null ? "UNAVAILABLE" : `${item.value} ${item.unit || fallbackUnit}`}</strong>
              <p>{item?.observedOn ? `${item.observedOn} 기준 · ${item.source}` : "공식 Credential 또는 최신 데이터가 없습니다."}</p>
            </article>
          );
        })}
      </div>
      <p className="market-context-boundary">현재 관측값은 화면의 배경정보일 뿐이며 미래수익률을 예측하거나 아래 Stress 가정을 자동 변경하지 않습니다.</p>
    </section>
  );
}
