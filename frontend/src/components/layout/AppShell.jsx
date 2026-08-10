import { useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";

const NAV_ITEMS = [
  ["/profile/summary", "내 금융 프로필"],
  ["/twin", "My Financial Twin"],
  ["/what-if", "What-if"],
  ["/scenario-lab", "Scenario Lab"],
  ["/goal", "Goal"],
];

export function AppShell() {
  const [open, setOpen] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const { provider, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    setLogoutError("");
    try {
      await logout();
      navigate("/", { replace: true });
    } catch {
      setLogoutError("로그아웃하지 못했습니다. 다시 시도해주세요.");
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <Link className="brand" to="/profile/summary" aria-label="FinTwin 홈">
          <span className="brand-mark" aria-hidden="true">F</span>
          <span>FinTwin</span>
        </Link>
        <button
          className="menu-button"
          type="button"
          aria-expanded={open}
          aria-controls="primary-navigation"
          onClick={() => setOpen((value) => !value)}
        >
          <span aria-hidden="true">☰</span>
          <span className="sr-only">메뉴 열기</span>
        </button>
        <nav id="primary-navigation" className={open ? "primary-nav primary-nav--open" : "primary-nav"}>
          {NAV_ITEMS.map(([to, label]) => (
            <NavLink
              key={to}
              to={to}
              onClick={() => setOpen(false)}
              className={({ isActive }) => (isActive ? "nav-link nav-link--active" : "nav-link")}
            >
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="account-actions">
          <span className="provider-badge">{provider === "kakao" ? "Kakao" : "Google"} 연결</span>
          <button className="button button--ghost" type="button" onClick={handleLogout}>로그아웃</button>
        </div>
      </header>
      {logoutError && <p className="shell-error" role="alert">{logoutError}</p>}
      <main className="app-main"><Outlet /></main>
      <footer className="app-footer">민감한 금융 원문은 외부 AI로 전송하지 않습니다.</footer>
    </div>
  );
}
