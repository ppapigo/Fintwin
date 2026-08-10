# Google·Kakao OAuth2 Login

## 인증 아키텍처

FinTwin MVP는 자체 JWT를 발급하지 않고 Spring Security OAuth2 Login과 서버 `HttpSession`을 사용한다.

```text
React
  -> /oauth2/authorization/{google|kakao}
  -> Provider 로그인/동의
  -> /login/oauth2/code/{registrationId}
  -> Spring Security token 교환 및 사용자 정보 검증
  -> Provider별 최소 Attribute Mapper
  -> OAuthIdentity 조회 또는 User/OAuthIdentity 생성
  -> FinTwinPrincipal(internal userId, provider)
  -> Session fixation 보호 후 HttpOnly JSESSIONID
  -> 고정 FRONTEND_BASE_URL/auth/callback
```

Provider Access/Refresh Token, 이메일, 전체 attributes, 응답 JSON은 DB에 저장하지 않는다. 로그인 뒤 Provider API를 사용하지 않으므로 `TransientOAuth2AuthorizedClientRepository`가 Authorized Client를 세션이나 DB에 보관하지 않는다. Google OIDC 검증에 사용된 ID Token은 인증 Principal 계약상 현재 서버 세션 수명 동안만 존재하며 API·로그·DB로 내보내지 않는다.

## 활성화와 환경변수

기본값은 `FINTWIN_OAUTH_ENABLED=false`다. 비활성화 상태에서도 금융 API는 인증 없이 열리지 않으며 임시 사용자 fallback은 없다. 활성화하려면 다음 값을 실행 환경 또는 운영 Secret 저장소에서 주입한다.

```text
FINTWIN_OAUTH_ENABLED=
FRONTEND_BASE_URL=
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=
CORS_ALLOWED_ORIGINS=
```

실제 값은 `.env.example`, `application.yaml`, 문서, Docker image, Git에 넣지 않는다. OAuth 활성화 상태에서 Google 또는 Kakao Client 설정이 하나라도 비어 있으면 애플리케이션 기동이 실패한다.

## Google Console 설정

1. Google Cloud Console에서 OAuth 동의 화면을 구성한다.
2. Web application OAuth Client를 생성한다.
3. 승인된 Redirect URI를 문자 단위로 정확히 등록한다.
4. FinTwin은 로그인 식별에 필요한 `openid` Scope만 요청하고 `sub`만 내부 Identity 키로 사용한다.
5. 이메일, 이름, 사진은 로그인 식별이나 자동 계정 연결에 사용하지 않는다.

Local Redirect URI:

```text
http://localhost:8080/login/oauth2/code/google
```

운영 Redirect URI 형식:

```text
https://<API_DOMAIN>/login/oauth2/code/google
```

## Kakao Developers 설정

1. Kakao Developers 애플리케이션에서 Kakao Login을 활성화한다.
2. REST API용 Client ID와 Client Secret을 발급·활성화한다.
3. Redirect URI를 문자 단위로 정확히 등록한다.
4. 사용자 정보 응답의 최상위 숫자 `id`만 문자열로 정규화해 Identity 키로 사용한다.
5. 닉네임, 프로필 이미지, 이메일, 성별, 연령대, 생일, 전화번호 Scope는 요청하지 않는다.

Local Redirect URI:

```text
http://localhost:8080/login/oauth2/code/kakao
```

운영 Redirect URI 형식:

```text
https://<API_DOMAIN>/login/oauth2/code/kakao
```

Kakao 연결 설정은 다음 Endpoint와 `client_secret_post`를 사용한다.

```text
authorization: https://kauth.kakao.com/oauth/authorize
token:         https://kauth.kakao.com/oauth/token
user info:     https://kapi.kakao.com/v2/user/me
```

## 사용자와 계정 정책

`OAuthIdentity(provider, providerSubject)`의 복합 UNIQUE가 동일 Provider 계정의 중복 생성을 최종 차단한다. 최초 로그인 충돌 시 생성 트랜잭션을 롤백한 뒤 이미 생성된 Identity를 다시 조회한다. 로그인만으로 Financial Profile을 만들거나 수정하지 않는다.

서로 다른 Google `sub`는 서로 다른 사용자다. Google과 Kakao가 같은 Subject 문자열 또는 이메일을 반환해도 별도 사용자다. 이메일 기반 자동 병합과 Provider 간 계정 연결은 지원하지 않는다. 향후 계정 연결은 로그인된 사용자의 명시적 재인증 기능으로 구현해야 한다.

## React 호출

로그인 시작은 브라우저 top-level navigation을 사용한다.

```javascript
window.location.assign(`${API_BASE_URL}/oauth2/authorization/google`);
window.location.assign(`${API_BASE_URL}/oauth2/authorization/kakao`);
```

로그인 성공과 실패는 설정된 Frontend Origin의 다음 고정 URL로만 돌아온다.

```text
/auth/callback?status=success
/auth/callback?status=failed&code=OAUTH_LOGIN_FAILED
```

요청의 `redirect` 또는 `returnUrl`은 사용하지 않는다. URL에는 User ID, Subject, 이메일, Token, Session ID, Provider 원문 오류가 없다.

현재 로그인 상태:

```javascript
const me = await fetch(`${API_BASE_URL}/api/auth/me`, {
  credentials: "include"
}).then(response => response.json());
// { authenticated: true, provider: "GOOGLE" }
```

## CSRF와 로그아웃

CSRF는 활성화되어 있다. React는 먼저 CSRF 토큰을 받고 변경 요청에 응답의 `headerName`을 사용한다. 이 CSRF 값은 금융정보나 로그인 Token이 아니다.

```javascript
const csrf = await fetch(`${API_BASE_URL}/api/auth/csrf`, {
  credentials: "include"
}).then(response => response.json());

await fetch(`${API_BASE_URL}/api/auth/logout`, {
  method: "POST",
  credentials: "include",
  headers: { [csrf.headerName]: csrf.token }
});
```

로그아웃은 세션과 SecurityContext를 무효화하고 `JSESSIONID`를 삭제한다. Provider 계정 자체 로그아웃이나 연결 해제는 수행하지 않는다.

## CORS와 세션 쿠키

- CORS Origin은 `CORS_ALLOWED_ORIGINS`의 명시적 React Origin만 허용한다. `*`는 거부한다.
- `allowCredentials=true`이며 `X-CSRF-TOKEN`, `X-XSRF-TOKEN` Header를 허용한다.
- 쿠키는 `HttpOnly`, `Path=/`, `SameSite=Lax`, 30분 timeout이다.
- local/test만 `Secure=false`, prod는 `Secure=true`다.
- 로그인 성공 시 기존 세션을 migration하여 fixation을 방어한다.

운영에서는 React와 API를 같은 사이트 또는 같은 최상위 도메인의 HTTPS 하위 도메인으로 배치한다. 서로 완전히 다른 사이트를 사용하면 `SameSite=Lax` 쿠키와 CSRF/CORS 정책을 별도로 재설계해야 한다.

## AWS Reverse Proxy 주의사항

prod는 Spring Boot의 native forwarded-header 처리를 사용한다. ALB/Reverse Proxy가 `X-Forwarded-Proto=https`와 Host를 정확히 설정해야 `{baseUrl}` Redirect URI가 HTTPS API 도메인으로 생성된다. EC2 애플리케이션 포트의 직접 인터넷 접근을 Security Group으로 차단해 신뢰할 수 없는 클라이언트가 forwarded header를 직접 주입하지 못하게 한다.

Provider Console의 운영 Redirect URI와 외부 HTTPS 주소는 문자 단위로 일치해야 한다. ALB health check는 공개 `/actuator/health`만 사용한다.

## MySQL 적용

prod는 `ddl-auto=validate`이므로 배포 전에 검토·백업 후 `docs/sql/oauth_identity_mysql8.sql`을 수동 적용한다. SQL은 기존 User ID와 Financial Profile FK를 보존하고 `oauth_identities` 테이블, 복합 UNIQUE, User FK, 인덱스를 추가한다. 애플리케이션이 SQL을 자동 실행하거나 기존 데이터를 삭제하지 않는다.

## 현재 한계

- 서버 메모리 세션은 단일 인스턴스 MVP 전용이다. 다중 인스턴스에서는 sticky session 또는 별도 세션 설계가 필요하다.
- Redis와 Spring Session은 현재 추가하지 않았다.
- Provider 간 계정 연결, 연결 해제, Provider 계정 로그아웃은 지원하지 않는다.
- 화면용 이름·사진·이메일을 수집·저장하지 않는다.
- Provider Secret 회전과 장애 대응은 배포 운영 절차에서 별도로 관리해야 한다.
