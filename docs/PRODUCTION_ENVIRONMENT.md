# Production Environment

운영값은 Git에 없는 `.env.prod` 또는 배포 플랫폼의 Secret 저장소에서만 주입한다. `docker inspect`, shell history, 지원 티켓에도 실제 값을 복사하지 않는다. `.env.prod.example`은 변수명만 제공하며 실행 가능한 Secret을 제공하지 않는다.

## 공개 Origin과 Nginx

| 변수 | 운영 기준 |
|---|---|
| `DOMAIN` | scheme/path 없는 실제 도메인, 예: `fintwin.example.com` |
| `FRONTEND_BASE_URL` | `https://<DOMAIN>` 정확히 한 Origin |
| `CORS_ALLOWED_ORIGINS` | 동일한 `https://<DOMAIN>`; wildcard 금지 |
| `NGINX_TLS_MODE` | 인증서 발급 후 `https` |
| `LETSENCRYPT_DIR` | 서버 인증서 디렉터리 절대 경로 |
| `HTTP_PORT`, `HTTPS_PORT` | 운영은 각각 `80`, `443` |
| `TZ` | 기본 `Asia/Seoul`; 로그 운영 기준은 별도로 명시 |

Frontend bundle은 상대 URL만 사용한다. `VITE_API_BASE_URL`, DB/OAuth/OpenAI Secret을 이미지 build argument로 전달하지 않는다.

## MySQL

| 변수 | 설명 |
|---|---|
| `MYSQL_DATABASE` | 새 전용 database 이름 |
| `MYSQL_USER` | root가 아닌 애플리케이션 사용자 |
| `MYSQL_PASSWORD` | 강한 고유 Password |
| `MYSQL_ROOT_PASSWORD` | 애플리케이션과 공유하지 않는 강한 root Password |

Compose가 내부 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 변환한다. MySQL은 host port를 갖지 않고 `database` 내부 network에만 연결된다. Container 내부 연결은 Docker network이므로 DB URL은 `useSSL=false`; 인터넷 구간은 존재하지 않는다.

## OAuth2

| 변수 | 설명 |
|---|---|
| `FINTWIN_OAUTH_ENABLED` | 운영 로그인 사용 시 `true` |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google server OAuth credential |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | Kakao server OAuth credential |

Credential은 Frontend에 전달되지 않는다. 운영 redirect URI는 다음 두 개를 Provider 콘솔에 정확히 등록한다.

```text
https://<DOMAIN>/login/oauth2/code/google
https://<DOMAIN>/login/oauth2/code/kakao
```

`FRONTEND_BASE_URL`은 로그인 성공/실패 후 `/auth/callback` 이동에만 사용한다. URL에 access token, OAuth subject, 내부 user ID를 넣지 않는다.

## OpenAI

| 변수 | 설명 |
|---|---|
| `FINTWIN_AI_ENABLED` | 사용하지 않으면 `false` |
| `OPENAI_BASE_URL` | 기본 공식 endpoint |
| `OPENAI_API_KEY` | AI가 `true`일 때만 필수인 서버 Secret |
| `OPENAI_MODEL` | 검증한 모델 이름 |
| `OPENAI_CONNECT_TIMEOUT` | 기본 `3s` |
| `OPENAI_READ_TIMEOUT` | 기본 `15s` |
| `OPENAI_MAX_OUTPUT_TOKENS` | 기본 `1200` |
| `OPENAI_MAX_RESPONSE_BYTES` | 기본 `65536` |

Adapter는 Responses API 요청에 `store=false`, 요청당 1회 호출(재시도 0회)을 유지한다. 외부로 나가는 값은 privacy allowlist DTO로 제한되고 Financial Profile, 거래 원문, 사용자 식별자, request vault는 전달하지 않는다. Provider body/request ID와 API key를 오류 응답이나 로그에 남기지 않는다.

## Spring 운영 보안값

`application-prod.yaml`은 다음을 고정한다.

- `ddl-auto=validate`, `open-in-view=false`, SQL/bind log OFF
- Session 30분, HttpOnly/Secure/SameSite=Lax/Path=/
- health만 Actuator 공개, detail 비공개
- stack trace, exception, binding detail, 내부 message 비공개
- graceful shutdown 30초
- `forward-headers-strategy=native`

Forwarded header는 Backend가 호스트에 노출되지 않고 Nginx와 같은 Docker `edge` network에서만 접근 가능하다는 경계에 의존한다. Security Group/host firewall에서 8080을 열면 이 신뢰 경계가 깨진다.

## Git에 절대 넣지 않는 파일

- `.env`, `.env.prod`, `.env.prod.local`, `frontend/.env*` 실제값
- `.certbot/`, private key, fullchain 사본
- `backups/`, dump, DB volume archive
- OAuth/OpenAI Secret, provider token/response
- 금융 원문, 거래 CSV/XLSX, 분석 결과 export
