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
- `/twin`, `/what-if`, `/scenario-lab`, `/goal`: 다음 단계 placeholder

프로필 화면은 사용자 ID와 내부 Profile ID를 요청하거나 표시하지 않습니다. 주요 금융목표는 현재 백엔드 계약에 없으므로 별도 Goal 모델이 구현될 때 연결합니다.
