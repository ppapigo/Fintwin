# FinTwin

FinTwin은 사용자의 금융 원문을 외부 AI에 노출하지 않고, 내부의 결정론적 금융 엔진으로 상태와 선택지를 분석하는 개인 금융 의사결정 서비스입니다.

## 구성

- `src/`: Java 21, Spring Boot 4.1 백엔드
- `frontend/`: React 19, Vite 8 JavaScript 프론트엔드
- `docs/`: API, 보안 경계, 단계별 구현 문서

## 로컬 실행

MySQL Docker를 시작한 뒤 백엔드를 실행합니다.

```powershell
docker compose up -d mysql
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

다른 터미널에서 프론트엔드를 실행합니다.

```powershell
cd frontend
npm install
npm run dev
```

Vite 개발 서버는 기본적으로 `http://localhost:5173`, Spring Boot는 `http://localhost:8080`을 사용합니다. 자세한 환경설정과 OAuth provider 등록 절차는 [프론트엔드 설정](docs/FRONTEND_SETUP.md) 및 [OAuth2 로그인](docs/OAUTH2_LOGIN.md)을 참고하세요.

## 검증

```powershell
cd frontend
npm test
npm run build

cd ..
.\gradlew.bat clean build
```

실제 비밀값은 `.env`나 운영 Secret 저장소에서 주입하고 Git에 커밋하지 않습니다.
