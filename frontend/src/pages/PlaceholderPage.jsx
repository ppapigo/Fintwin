export function PlaceholderPage({ eyebrow, title, description }) {
  return (
    <section className="content-page placeholder-page">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p>{description}</p>
      <div className="placeholder-grid" aria-label="준비 중인 기능">
        <article><span>01</span><h2>State</h2><p>현재 금융 상태를 기준점으로 사용합니다.</p></article>
        <article><span>02</span><h2>Simulate</h2><p>결정론적 엔진이 가능한 경로를 계산합니다.</p></article>
        <article><span>03</span><h2>Compare</h2><p>결과와 위험을 같은 기준으로 비교합니다.</p></article>
      </div>
      <p className="coming-soon">다음 개발 단계에서 연결됩니다.</p>
    </section>
  );
}
