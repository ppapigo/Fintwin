import { lazy, Suspense } from "react";
import { Route, Routes } from "react-router-dom";
import { ProtectedRoute } from "./auth/ProtectedRoute";
import { LoadingScreen } from "./components/common/LoadingScreen";
import { AppShell } from "./components/layout/AppShell";
import { AuthCallbackPage } from "./pages/AuthCallbackPage";
import { FinancialProfilePage } from "./pages/FinancialProfilePage";
import { LandingPage } from "./pages/LandingPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { PlaceholderPage } from "./pages/PlaceholderPage";
import { ProfileSummaryPage } from "./pages/ProfileSummaryPage";

const FinancialTwinPage = lazy(() => import("./pages/FinancialTwinPage")
  .then((module) => ({ default: module.FinancialTwinPage })));
const WhatIfPage = lazy(() => import("./pages/WhatIfPage")
  .then((module) => ({ default: module.WhatIfPage })));

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/profile/setup" element={<FinancialProfilePage mode="create" />} />
          <Route path="/profile/edit" element={<FinancialProfilePage mode="edit" />} />
          <Route path="/profile/summary" element={<ProfileSummaryPage />} />
          <Route path="/twin" element={<Suspense fallback={<LoadingScreen label="My Financial Twin을 준비하고 있습니다." />}><FinancialTwinPage /></Suspense>} />
          <Route path="/what-if" element={<Suspense fallback={<LoadingScreen label="Privacy-first What-if를 준비하고 있습니다." />}><WhatIfPage /></Suspense>} />
          <Route path="/scenario-lab" element={<PlaceholderPage eyebrow="SCENARIO LAB" title="기준안과 대안을 나란히" description="같은 가정과 기간에서 시나리오 A/B를 비교합니다." />} />
          <Route path="/goal" element={<PlaceholderPage eyebrow="GOAL SOLVER" title="목표에서 현재로 역산" description="목표 금액과 기간을 만족하기 위한 월별 조건을 계산합니다." />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
