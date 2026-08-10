# Frontend Setup

## 실제 스택

- React 19.2.8, React DOM 19.2.8
- React Router DOM 7.18.2
- Vite 8.2.1, JavaScript JSX
- Vitest 4.1.10, React Testing Library, jsdom
- 검증 환경: Node.js 24.19.0, npm 11.17.0

TypeScript, Axios, 별도 전역 상태관리, UI 프레임워크는 사용하지 않습니다.

## 설치와 실행

백엔드가 `http://localhost:8080`에서 실행 중인 상태에서 다음을 실행합니다.

```powershell
cd frontend
npm install
npm run dev
```

브라우저 주소는 `http://localhost:5173`입니다. Vite proxy는 `/api`, `/oauth2`, `/login/oauth2`, `/actuator` 요청을 `VITE_API_PROXY_TARGET`로 전달합니다. 브라우저 관점에서는 동일 출처 요청이므로 세션 쿠키와 CSRF 흐름을 로컬에서도 재현할 수 있습니다.

## 환경변수

`frontend/.env.example`을 참고합니다.

- `VITE_API_BASE_URL`: 배포 시 API origin이 분리된 경우의 공개 주소. 로컬에서는 비워 proxy 사용을 권장합니다.
- `VITE_API_PROXY_TARGET`: 로컬 Vite proxy가 연결할 Spring Boot 주소. 기본값은 `http://localhost:8080`입니다.

프론트 환경변수는 빌드 결과에 노출될 수 있으므로 OAuth Secret, DB 접속정보, 세션 Secret 등 비밀값을 넣지 않습니다.

## OAuth2 로컬 설정

로그인 버튼은 AJAX로 토큰을 요청하지 않고 브라우저를 다음 백엔드 URL로 이동시킵니다.

- Google: `/oauth2/authorization/google`
- Kakao: `/oauth2/authorization/kakao`

Spring Security가 provider callback을 처리한 뒤 고정 프론트 경로 `/auth/callback`으로 이동합니다. Provider 콘솔에는 백엔드 callback URI를 등록해야 합니다.

- Google: `http://localhost:8080/login/oauth2/code/google`
- Kakao: `http://localhost:8080/login/oauth2/code/kakao`

Google Client ID/Secret과 Kakao Client ID/Secret은 백엔드 환경변수로 주입합니다. 허용 origin은 백엔드 `CORS_ALLOWED_ORIGINS`에 `http://localhost:5173`이 포함되어야 합니다. 운영에서는 실제 HTTPS frontend origin과 callback URI로 교체합니다.

## 테스트와 빌드

```powershell
npm test
npm run build
```

실제 Google/Kakao 로그인은 provider 계정과 Client Secret이 있어야 하므로 자동 테스트에서는 API와 브라우저 이동 경계를 mock 처리합니다. 사용자 수동 검증 순서는 다음과 같습니다.

1. MySQL, 백엔드, 프론트엔드를 순서대로 실행합니다.
2. Google 또는 Kakao 로그인 버튼을 누릅니다.
3. `/auth/callback` 이후 프로필이 없으면 `/profile/setup`으로 이동하는지 확인합니다.
4. 10개 값을 저장하고 `/profile/summary`에서 계산과 v1 이력을 확인합니다.
5. `/profile/edit`에서 값을 수정하고 v2가 생성되며 v1이 유지되는지 확인합니다.
6. 로그아웃 후 보호 라우트가 랜딩으로 이동하는지 확인합니다.
