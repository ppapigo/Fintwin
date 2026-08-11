# FinTwin Production Docker

## 구조

```text
Internet :80/:443
        |
   frontend (Nginx + React SPA)
        | edge network
   backend:8080 (host port 없음)
        | database network (internal)
   mysql:3306 (host port 없음, named volume)
```

`compose.yaml`은 기존 로컬 MySQL 전용이며 변경하지 않는다. 운영 및 production-like 검증은 반드시 `compose.prod.yaml`과 별도 환경 파일을 사용한다. Backend는 Java 21 Temurin JRE에서 UID 10001 비-root 사용자로 실행되고, Frontend 최종 이미지에는 Nginx와 빌드된 정적 파일만 남는다.

## 사전 조건

- Docker Engine과 Compose v2
- 운영에서는 먼저 도메인의 A/AAAA 레코드를 인스턴스 공인 주소에 연결
- `.env.prod.example`을 참고해 서버에서만 `.env.prod` 작성 후 `chmod 600 .env.prod`
- `LETSENCRYPT_DIR` 디렉터리를 미리 만들고 소유권을 배포 사용자로 제한
- 기존 DB를 사용할 때는 자동 초기화가 실행되지 않음을 확인하고 먼저 백업

필수값이 비어 있으면 `${VAR:?message}` 검증으로 Compose 렌더링 단계에서 실패한다. 예제 파일에는 작동하는 기본 Password나 Secret이 없다.

## 이미지와 Compose 검증

```bash
docker compose -f compose.yaml config --quiet
docker compose --env-file .env.prod -f compose.prod.yaml config --quiet
docker compose --env-file .env.prod -f compose.prod.yaml build
```

Backend 빌드 단계는 Gradle Wrapper로 `bootJar -x test`를 실행한다. 테스트는 이미지 빌드 전에 호스트/CI에서 `./gradlew clean build`로 별도 통과시켜야 한다. Frontend 빌드는 lockfile 기반 `npm ci --ignore-scripts` 후 `npm run build`를 실행하며, `VITE_API_BASE_URL`을 설정하지 않아 동일 Origin 상대 경로를 유지한다.

## localhost production-like 기동

localhost에서는 인증서 발급을 시도하지 않는다. Git에 포함되지 않는 별도 환경 파일에 다음처럼 설정한다.

```text
DOMAIN=localhost
FRONTEND_BASE_URL=http://localhost:18080
CORS_ALLOWED_ORIGINS=http://localhost:18080
NGINX_TLS_MODE=http
HTTP_PORT=18080
HTTPS_PORT=18443
LETSENCRYPT_DIR=<absolute-empty-directory>
FINTWIN_OAUTH_ENABLED=false
FINTWIN_AI_ENABLED=false
```

OAuth가 비활성화되어도 Compose의 fail-fast 검사를 위해 OAuth credential 변수에는 `local-validation-only` 같은 비밀이 아닌 검증 전용 값을 넣는다. DB Password는 이 임시 스택에만 쓰는 강한 무작위 값으로 생성한다.

```bash
docker compose --env-file .env.prod.local -f compose.prod.yaml up -d --build
docker compose --env-file .env.prod.local -f compose.prod.yaml ps
curl -i http://localhost:18080/nginx-health
curl -i http://localhost:18080/actuator/health
curl -i http://localhost:18080/api/auth/csrf
curl -i http://localhost:18080/api/auth/me
docker compose --env-file .env.prod.local -f compose.prod.yaml exec frontend nginx -t
```

`/api/auth/me`와 인증이 필요한 금융/XLSX API는 세션이 없으면 거부되어야 한다. 실제 XLSX 템플릿 다운로드·업로드는 OAuth 세션이 있는 환경에서 검증한다. 업로드는 Spring 2 MiB 파일 제한/3 MiB 요청 제한과 Nginx 3 MiB 제한을 함께 적용한다.

호스트 공개 포트 확인:

```bash
docker compose --env-file .env.prod.local -f compose.prod.yaml ps
docker port fintwin-prod-backend-1
docker port fintwin-prod-mysql-1
```

두 `docker port` 결과는 비어 있어야 한다. 종료할 때 `down -v`를 사용하지 않는다.

```bash
docker compose --env-file .env.prod.local -f compose.prod.yaml down
```

## Nginx 동작

- `/api/`, `/oauth2/`, `/login/`, `/logout`, `/actuator/`를 Backend로 전달
- OAuth callback `/login/oauth2/code/{provider}`의 Host와 HTTPS scheme을 전달
- `X-Forwarded-Proto`, `Host`, `X-Forwarded-For/Host/Port` 설정
- API/OAuth/Actuator 응답은 `Cache-Control: no-store`
- Nginx가 Backend 오류 body를 HTML로 교체하지 않음
- SPA route는 `/index.html`로 fallback, fingerprint 정적 asset은 1년 cache
- `client_max_body_size 3m`, WebSocket 설정 없음, server version 숨김
- CSP, HSTS, frame/content-type/referrer/permissions header 적용

`NGINX_TLS_MODE=http`는 localhost와 인증서 최초 발급 부트스트랩에만 사용한다. 운영 서비스는 인증서 발급 후 반드시 `https`로 바꾼다.

## 최초 배포와 재배포

최초 배포는 `DATABASE_INITIALIZATION.md`의 빈 DB 조건을 먼저 확인한 후 다음 순서로 진행한다.

1. 환경 파일 및 인증서 디렉터리 권한 확인
2. HTTP bootstrap으로 Nginx 기동
3. DNS 확인 후 Certbot webroot 인증서 발급
4. `NGINX_TLS_MODE=https`로 변경해 Frontend 재생성
5. OAuth callback, session, CSRF, health 확인

재배포 전에는 DB 백업을 만든 뒤 소스/이미지를 갱신한다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml build --pull
docker compose --env-file .env.prod -f compose.prod.yaml up -d --remove-orphans
docker compose --env-file .env.prod -f compose.prod.yaml ps
```

이 구성은 단일 인스턴스 rolling/무중단 배포를 지원하지 않는다. Backend 또는 Frontend 교체 중 짧은 중단이 발생할 수 있다.
