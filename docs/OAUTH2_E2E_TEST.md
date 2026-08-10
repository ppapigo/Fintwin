# OAuth2 Local E2E Test

## 검증 정보

- 검증일: 2026-08-10 (Asia/Seoul)
- Backend: Java 21, Spring Boot 4.1.0, Spring Security 7.1 계열
- Frontend: React 19.2.8, Vite 8.2.1, JavaScript
- Database: Docker MySQL 8.4.11, 기존 volume 유지
- 인증: Spring Security OAuth2 Login, 서버 `HttpSession`, CSRF 활성화
- 공개 주소: Backend `http://localhost:8080`, Frontend `http://localhost:5173`

Secret, Client ID, Provider Subject, Authorization Code, State, Token, Cookie 값과 내부 사용자 ID는 조회 결과나 이 문서에 기록하지 않았다.

## 자동 테스트 기준선

- `gradlew.bat clean build`: 성공
- `frontend/npm test`: 12개 파일, 29개 테스트 성공
- `frontend/npm run build`: 성공
- OAuth E2E 전에 실패한 기존 테스트는 없었다.

## MySQL Schema

Metadata와 집계만 조회했고 OAuth Subject 원문은 조회하지 않았다.

- `users.id`: `BIGINT`, PK, `AUTO_INCREMENT`
- `oauth_identities.id`: `BIGINT`, PK, `AUTO_INCREMENT`
- User FK: 존재
- `(provider, provider_subject)` UNIQUE: 존재
- `user_id` index: 존재
- Provider Token column: 없음
- 최초 집계: Google identity 1개, Kakao identity 1개, 사용자 2명
- 최초 Profile 집계: 한 사용자에게 v1 한 개

발견 당시 `provider_subject` collation은 `utf8mb4_0900_ai_ci`였다. 엔티티와 문서 SQL은 case-sensitive `utf8mb4_0900_bin`을 명시하도록 수정했다. 사용자 승인 후 로컬 DB 컬럼도 같은 collation으로 변경했고 Metadata, 중복 0건, 고아 identity 0건을 재확인했다.

## 애플리케이션 기동과 공개 경계

- `.env` 필수 OAuth 설정은 값 노출 없이 모두 설정됨을 확인했다.
- MySQL container: `healthy`
- Backend health: `UP`
- Frontend: HTTP 200
- 비인증 `/api/auth/me`: 403, React는 anonymous 랜딩으로 처리
- `/api/auth/csrf`: 200, 공개 header/parameter 이름과 비어 있지 않은 토큰 확인
- 허용 Origin `http://localhost:5173`: credentialed CORS 허용
- 비허용 Origin: `Access-Control-Allow-Origin` 없음
- Frontend production bundle: Google/Kakao Client ID와 Secret 문자열 없음
- 브라우저 console: 검증한 화면에서 error/warning 없음

## Google 실제 로그인과 재로그인

브라우저에 기존 Google SSO와 동의가 남아 있어 계정 선택 화면 없이 provider callback이 자동 완료됐다. 계정 선택이나 동의는 자동화하지 않았다.

- 실제 Google OAuth redirect와 callback 성공
- React provider 표시: Google
- 보호된 Profile 요약 화면 진입
- 동일 계정 재로그인 후 Google identity: 1개 유지
- 새 User 생성 없음
- 기존 OAuthIdentity 재사용
- `lastLoginAt > createdAt` 집계 확인
- 기존 Profile v2와 v1 History 유지
- 로그인 URL과 callback 화면에 Token, User ID 없음

검증 시작 전에 Google identity와 User가 이미 존재했으므로 이번 실행에서 신규 Google User 1건 생성은 다시 만들지 않았다. 기존 DB나 identity를 삭제하지 않는 규칙을 우선했다.

## Financial Profile E2E

Google 사용자에게 기존 v1이 있어 이를 기준으로 전체 수정 E2E를 수행했다. 입력은 실제 금융정보가 아닌 다음 명시적 샘플을 사용했다.

- 월 소득: 3,000,000원
- 현금성 자산: 2,000,000원
- 예금: 3,000,000원
- 투자자산: 1,000,000원
- 총 대출잔액: 10,000,000원
- 대출금리: 4.5%
- 월 고정지출: 800,000원
- 월 변동지출: 700,000원
- 월 저축액: 300,000원
- 월 투자액: 200,000원

결과:

- 수정 전 현재 Profile: v1
- 전체 수정 요청 성공
- 새 불변 Profile v2 생성
- v1 보존
- History에 v2와 v1 표시
- 새로고침 후 인증, v2, v1 History 유지
- DB 집계: Profile 2개, Profile 사용자 1명, v1 1개, v2 1개
- 다른 provider 사용자에게 Profile 자동 생성 또는 노출 없음

최초 v1 생성은 검증 시작 전에 존재했으므로 새로 생성하지 않았다.

## Logout과 Session/CSRF

- React Logout 성공
- 로그아웃 후 랜딩 이동
- 뒤로가기로 보호 화면과 금융 데이터 재노출 없음
- SecurityContext와 보호 라우트 접근 차단 확인
- CSRF session cookie: `HttpOnly`, `Path=/`, `SameSite=Lax`
- local cookie: `Secure=false`
- 잘못된 CSRF header: 403
- 올바른 CSRF를 포함한 logout: 204
- 로그아웃 후 이전 CSRF로 금융 변경 요청: 403

Cookie, Session ID와 CSRF Token 값 자체는 출력하지 않았다.

## Kakao 실제 로그인

Kakao 인증과 동의는 사용자가 브라우저에서 직접 완료했다.

- 실제 Kakao OAuth callback 성공
- React provider 표시: Kakao
- Profile이 없는 Kakao 사용자는 `/profile/setup`으로 이동
- Kakao identity: 1개 유지
- Kakao 재로그인에서 새 User 또는 identity 생성 없음
- Kakao `lastLoginAt > createdAt` 집계 확인
- Google/Kakao identity 사용자 수: 2명으로 분리
- 이메일 기반 병합 없음
- Kakao Profile 자동 생성 없음
- Kakao logout 성공
- Provider Token, 이메일, 닉네임, 프로필 원문 저장 column 없음

검증 시작 전에 Kakao identity와 User가 이미 존재했으므로 신규 Kakao User 생성은 다시 수행하지 않았다.

## 안전한 실패 경로

- 잘못된 Google callback 직접 접근: 고정 Frontend callback으로 302
- callback 공개 결과: `status=failed`, `code=OAUTH_LOGIN_FAILED`
- 임의 `redirect`와 `returnUrl`: 반영되지 않음
- callback URL에 State, Token, Subject, User ID 없음
- React 실패 화면: 안전한 사용자 메시지만 표시
- 임의 redirect, dummy token marker, 내부 오류 코드가 화면에 렌더링되지 않음

Google/Kakao provider의 실제 취소 버튼 흐름은 기존 SSO 때문에 로그인 화면이 자동 통과되어 재현하지 못했다. 이 항목은 성공으로 기록하지 않는다.

## DB Identity 집계

최종 집계는 Subject를 제외하고 확인했다.

- Google: identity 1개, 재로그인 갱신 확인
- Kakao: identity 1개, 재로그인 갱신 확인
- distinct identity user: 2명
- 전체 User: 2명
- 전체 OAuthIdentity: 2개
- 전체 Profile: 2개, 한 사용자에게만 연결
- Provider Token column: 없음

## 발견한 문제와 수정

1. `PEMHTTPD-x64`가 8080을 점유해 Spring Boot 대신 Apache 404를 반환했다.
   - 로컬 E2E 동안 서비스만 중지했다.
   - 시작 유형 `Automatic`은 변경하지 않았다.
2. 과거 로컬 `users.id`에 `AUTO_INCREMENT`가 없어 최초 OAuth User insert가 실패했다.
   - 데이터가 없는 상태에서 FK를 동일 규칙으로 복원하며 `AUTO_INCREMENT`를 적용했고 현재 Metadata로 확인했다.
3. `provider_subject`가 case-insensitive collation으로 생성됐다.
   - `OAuthIdentity.providerSubject`에 Hibernate `@Collate("utf8mb4_0900_bin")`을 추가했다.
   - case-sensitive identity 계약 회귀 테스트를 추가했다.
4. `docs/sql/oauth_identity_mysql8.sql`이 기존 Financial Profile FK 때문에 `users.id` 변경에 실패할 수 있었다.
   - Metadata에서 기존 FK 이름을 찾아 변경 전에 해제하고 동일 이름·규칙으로 복원하도록 수정했다.
   - Subject 원문을 출력하던 postflight 쿼리를 집계와 collation Metadata 조회로 교체했다.

## 미검증 항목

- Google provider 실제 취소 버튼
- Kakao provider 실제 취소 버튼
- 빈 DB에서의 신규 Google User 생성: 기존 데이터 삭제 금지로 미실행
- 빈 DB에서의 신규 Kakao User 생성: 기존 데이터 삭제 금지로 미실행
- Cookie/Session ID 값 비교를 통한 fixation 관찰: 비밀값 비출력 규칙으로 미실행
- 실제 세션 timeout 경과: 장시간 대기 대신 logout 후 이전 CSRF·세션 거부만 검증
- 운영 HTTPS, prod Secure cookie와 운영 provider callback
- 다중 인스턴스 session 동작

## 최종 회귀 검증

- Backend `clean build`: 54개 suite, 260개 테스트 성공, 실패·오류·skip 0개
- 변경된 엔티티 매핑과 로컬 MySQL로 Backend 재기동: 성공
- `/actuator/health`: `UP`
- Frontend `npm test`: 12개 파일, 29개 테스트 성공
- Frontend `npm run build`: 성공, 41개 module 변환

## 재검증 명령

```powershell
.\gradlew.bat clean build

cd frontend
npm test
npm run build
```

자동 commit과 push는 수행하지 않았다.
