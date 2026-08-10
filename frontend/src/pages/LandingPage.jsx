import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { startOAuthLogin } from "../api/authApi";
import { getCurrentProfile } from "../api/financialProfileApi";
import { useAuth } from "../auth/useAuth";
import { LoadingScreen } from "../components/common/LoadingScreen";

const VALUE_CARDS = [
  ["01", "Financial State", "소득·자산·부채를 하나의 결정 가능한 상태로 정리합니다."],
  ["02", "Deterministic Simulation", "LLM과 분리된 계산 엔진으로 선택지의 결과를 비교합니다."],
  ["03", "Privacy Boundary", "금융 원문과 계좌 정보는 외부 AI 경계를 넘지 않습니다."],
];

export function LandingPage() {
  const { status, refreshAuth } = useAuth();
  const navigate = useNavigate();
  const routed = useRef(false);
  const [routing, setRouting] = useState(false);

  useEffect(() => {
    if (status !== "authenticated" || routed.current) return;
    routed.current = true;
    setRouting(true);
    getCurrentProfile()
      .then((profile) => navigate(profile ? "/profile/summary" : "/profile/setup", { replace: true }))
      .catch(() => {
        routed.current = false;
        setRouting(false);
      });
  }, [navigate, status]);

  if (routing) return <LoadingScreen label="내 Financial Twin을 준비하고 있습니다." />;

  return (
    <main className="landing">
      <header className="landing-header">
        <a className="brand" href="#top"><span className="brand-mark">F</span><span>FinTwin</span></a>
        <a className="text-link" href="#privacy">Privacy first</a>
      </header>

      <section className="hero" id="top">
        <div className="hero-copy">
          <p className="eyebrow">PRIVATE FINANCIAL DECISION INTELLIGENCE</p>
          <h1>당신의 금융 선택을<br /><span>숫자로 먼저 살아봅니다.</span></h1>
          <p className="hero-description">
            FinTwin은 챗봇이 아닙니다. 나의 금융 상태를 내부에서 분석하고, 결정론적 시뮬레이션으로
            목표와 제약을 만족하는 선택지를 탐색하는 개인 금융 의사결정 서비스입니다.
          </p>
          <div className="login-stack" aria-label="소셜 로그인">
            <button className="social-button social-button--google" type="button" onClick={() => startOAuthLogin("google")}>
              <span aria-hidden="true">G</span> Google로 시작하기
            </button>
            <button className="social-button social-button--kakao" type="button" onClick={() => startOAuthLogin("kakao")}>
              <span aria-hidden="true">K</span> Kakao로 시작하기
            </button>
          </div>
          {status === "error" && (
            <div className="inline-notice" role="alert">
              인증 서버 상태를 확인할 수 없습니다.
              <button className="text-button" type="button" onClick={() => refreshAuth({ showLoading: true })}>다시 확인</button>
            </div>
          )}
        </div>
        <div className="twin-visual" aria-label="Financial Twin 개념 그래픽">
          <div className="orbit orbit--outer" />
          <div className="orbit orbit--inner" />
          <div className="twin-core"><span>YOUR</span><strong>FINANCIAL<br />TWIN</strong><small>PRIVACY SEALED</small></div>
          <span className="metric metric--income">INCOME<br /><strong>+ stable</strong></span>
          <span className="metric metric--risk">RISK<br /><strong>checked</strong></span>
          <span className="metric metric--goal">GOAL<br /><strong>solved</strong></span>
        </div>
      </section>

      <section className="value-grid" aria-label="FinTwin 핵심 가치">
        {VALUE_CARDS.map(([number, title, description]) => (
          <article key={number} className="value-card">
            <span>{number}</span><h2>{title}</h2><p>{description}</p>
          </article>
        ))}
      </section>

      <section className="privacy-strip" id="privacy">
        <p className="eyebrow">STRICT PRIVACY BOUNDARY</p>
        <h2>당신의 금융 원문은<br />당신의 경계 안에 머뭅니다.</h2>
        <p>거래내역, 계좌번호, 금융기관명은 외부 LLM에 전달하지 않습니다.</p>
      </section>
    </main>
  );
}
