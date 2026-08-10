# FinTwin Frontend

React JavaScript와 Vite로 구현한 FinTwin MVP 웹 클라이언트입니다. 서버 세션과 CSRF를 사용하며 인증 토큰을 브라우저 저장소에 보관하지 않습니다.

## Commands

```powershell
npm install
npm run dev
npm test
npm run build
npm run preview
```

로컬 개발에서는 `VITE_API_BASE_URL`을 비워 동일 출처 상대 경로를 사용하고, Vite proxy가 API와 OAuth 요청을 Spring Boot로 전달합니다. 설정값은 `.env.example`을 참고하세요.

## Routes

- `/`: 랜딩 및 Google/Kakao 로그인 시작
- `/auth/callback`: 고정 OAuth 완료 화면
- `/profile/setup`: Financial Profile 온보딩
- `/profile/summary`: 최신 프로필 및 버전 이력
- `/profile/edit`: 전체 프로필 수정과 새 버전 생성
- `/twin`: 최신 Profile 기반 12·36·60개월 Baseline Simulation 대시보드
- `/what-if`: Privacy Preview 기반 자연어 또는 AI 없는 직접 이벤트 What-if 비교
- `/scenario-lab`, `/goal`: 다음 단계 placeholder

프로필 화면은 사용자 ID와 내부 Profile ID를 요청하거나 표시하지 않습니다. 주요 금융목표는 현재 백엔드 계약에 없으므로 별도 Goal 모델이 구현될 때 연결합니다.

## What-if

자연어 모드는 실행 전 `POST /api/privacy/scenario-payload-preview`의 토큰화 Payload를 표시하고 사용자의 명시적 승인을 요구합니다. 원문이 변경되면 승인을 폐기합니다. SAFE Preview 후 `POST /api/agent/natural-language`를 호출하며, AI는 이벤트 구조화에만 사용되고 금융 계산은 로컬 결정론적 엔진이 수행합니다.

직접 입력 모드는 금융 이벤트 6종을 `POST /api/simulations/compare`로 전달하며 외부 AI를 호출하지 않습니다. 두 모드는 `/twin`과 같은 Assumption UI·Validation을 공유합니다. 현재 자연어 응답은 최종 요약만 제공하므로 월별 Chart와 Checkpoint는 직접 입력 결과에서만 표시합니다. 자세한 Privacy·Mapping·오류 계약은 [`../docs/FRONTEND_WHAT_IF.md`](../docs/FRONTEND_WHAT_IF.md)를 참고하세요.
