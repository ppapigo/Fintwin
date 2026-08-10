# Frontend Security Boundary

## 인증과 세션

- OAuth2 로그인은 브라우저 redirect로만 시작합니다.
- 인증 여부는 서버 세션과 `GET /api/auth/me`만 신뢰합니다.
- access token, refresh token, JWT, provider token을 프론트에서 생성·수신·저장하지 않습니다.
- 세션 쿠키는 브라우저가 관리하며 모든 API 요청은 `credentials: include`를 사용합니다.

## CSRF

- 첫 상태 변경 요청 전에 `GET /api/auth/csrf`로 토큰을 받습니다.
- CSRF 토큰은 JavaScript 모듈 메모리에만 유지합니다.
- localStorage, sessionStorage, IndexedDB, cookie에 직접 저장하지 않습니다.
- 상태 변경 실패를 자동 재시도하지 않습니다. 401/403이면 메모리 토큰을 폐기하고, 사용자가 다시 제출할 때 새 토큰을 받아 중복 생성 위험을 피합니다.

## 금융정보와 식별자

- 프론트 요청은 현재 인증 Principal 경계만 사용하며 사용자 ID를 보내지 않습니다.
- 내부 Profile ID, 이전 Profile ID, 사용자 ID는 응답 정규화 시 제거하고 상태와 UI에 보관하지 않습니다.
- 계좌번호, 금융기관명, 거래 원문 입력란을 제공하지 않습니다.
- Financial Profile의 10개 계약 필드 외 추가 데이터를 생성·수정 payload에 포함하지 않습니다.
- 브라우저 로그에 금융값, OAuth 응답, 세션 또는 CSRF 토큰을 출력하지 않습니다.

## 오류 처리

- 서버의 최상위 raw 보안 오류 메시지는 사용자에게 그대로 렌더링하지 않습니다.
- 상태 코드와 공개 오류 코드에 따라 안전한 한국어 메시지로 변환합니다.
- DTO validation의 필드 오류만 해당 입력 항목에 연결합니다.
- callback query의 token, redirect, 내부 오류 코드는 사용하거나 화면에 출력하지 않습니다.

## 배포 확인

- HTTPS 적용
- 정확한 frontend origin만 credentialed CORS에 허용
- 운영 OAuth callback URI 등록
- `VITE_API_BASE_URL`에는 공개 API 주소만 설정
- 소스맵과 관측 도구가 금융 payload를 수집하지 않는지 검토
