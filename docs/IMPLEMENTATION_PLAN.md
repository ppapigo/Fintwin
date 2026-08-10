# FinTwin 구현 계획

기준 패키지는 `com.fintwin.fintwin`이며 package-by-feature를 사용한다. 계산 코어는 프레임워크와 분리하고, 외부 AI에는 비식별·집계·허용 목록 데이터만 전달한다.

## 1. 기반 설정

- 목표: 프로필별 DB, 오류 응답, CORS, health와 최소 도메인 기반을 확립한다.
- 주요 클래스: `SecurityConfig`, `GlobalExceptionHandler`, `User`, `FinancialProfile`, `FinancialProfileService`.
- 완료 조건: local은 MySQL/update, test는 H2/create-drop, prod는 MySQL/validate이고 비밀값이 환경변수로 주입된다.
- 테스트: Context, `/actuator/health`, 서비스 생성·조회, DTO Validation.

## 2. Financial Profile

- 목표: 사용자 재무상태를 UPDATE하지 않는 불변 스냅샷 버전 집합으로 입력·조회한다.
- 주요 클래스: `FinancialProfile`, `FinancialProfileCreateRequest`, `FinancialProfileUpdateRequest`, Profile Repository/Service/Controller.
- 버전 모델: 최초 등록은 version 1이며, 수정은 직전 ID를 `previousProfileId`로 가리키는 version N+1 행을 INSERT한다. 최신 Profile은 사용자별 가장 높은 version으로 조회한다.
- API: `POST /api/financial-profiles`, `GET|PUT /api/financial-profiles/current`, `GET /api/financial-profiles/history`, `GET /api/financial-profiles/{profileId}`. 기존 `GET /me`는 current 별칭으로 유지한다.
- 동시성: 사용자 행 `PESSIMISTIC_WRITE` 잠금으로 동일 사용자의 버전 생성을 직렬화하고 `(user_id, profile_version)` 유일성 제약을 최종 방어선으로 사용한다.
- 완료 조건: 금액·이율이 `BigDecimal`이고 과거 Profile이 변경되지 않으며 소유 사용자만 개별 스냅샷을 조회한다.
- 테스트: 최초/중복 생성, 버전 증가, 과거값 불변, 최신/이력 정렬, 소유권, Validation, 복합 유일성 제약.

## 3. 순수 Java 금융 시뮬레이션 엔진

- 상태: 완료.
- 목표: 최신 불변 Financial Profile을 입력으로 월 단위 현금흐름·자산·부채를 결정론적으로 계산한다.
- 주요 클래스: `SimulationInput`, `SimulationAssumptions`, `MonthlyFinancialSimulationEngine`, `MoneyMath`, `BaselineSimulationService`.
- API: `POST /api/simulations/baseline`에서 12·36·60개월 결과와 12·36·60개월 이내 체크포인트를 제공한다.
- 완료 조건: 코어 패키지가 Spring/JPA/AI를 import하지 않고 동일 입력에 동일 결과를 내며 결과를 DB에 저장하지 않는다.
- 테스트: 성장·물가, 예금·투자수익, 이자·원금·음의 상환, 저축·투자 회계, 현금 부족, 반올림, 최신 Profile 불변성.

## 4. 금융 엔진 골든 테스트

- 목표: 검증된 대표 케이스를 변경 감지 기준으로 고정한다.
- 주요 클래스: `GoldenScenario`, `GoldenResultLoader`, 시뮬레이션 fixture.
- 완료 조건: 승인된 입력/월별 결과가 버전 관리되고 의도 없는 수치 변화가 실패한다.
- 테스트: 정상·부채 과다·현금 고갈·장기 복리 골든 케이스.

## 5. 시나리오와 Financial Event

- 목표: 소득 변화, 지출, 대출, 투자 등 시간 기반 사건을 모델링한다.
- 주요 클래스: `Scenario`, `FinancialEvent`, `IncomeChange`, `ExpenseEvent`, `LoanEvent`.
- 완료 조건: 이벤트 순서·발생 월·우선순위가 명시되고 시뮬레이션 입력으로 변환된다.
- 테스트: 같은 달 이벤트 순서, 반복 이벤트, 무효 기간, 이벤트 조합.

## 6. Scenario A/B Comparison

- 목표: 두 선택지의 현금·순자산·부채·목표 달성 차이를 비교한다.
- 주요 클래스: `ScenarioComparisonService`, `ComparisonResult`, `MetricDelta`.
- 완료 조건: 동일 가정 기준의 차이와 판단 근거가 DTO로 제공된다.
- 테스트: 동일 시나리오, 우세/열세, 지표별 trade-off, 기간 불일치.

### 5~6단계 구현 상태 (완료)

- 불변 `FinancialEvent` 6종과 요청 DTO를 분리했다. 이벤트는 저장하지 않으며 계산 코어는 Jackson, HTTP, Spring, JPA, AI에 의존하지 않는다.
- `MonthlyAdjustmentProvider`를 기존 월별 엔진에 주입해 baseline과 what-if가 동일 계산 코드를 사용한다.
- `POST /api/simulations/compare`가 최신 Profile 스냅샷을 읽어 baseline/what-if 전체 결과, 체크포인트·최종 delta, 영향 요약과 경고를 반환한다.
- 이벤트 순서 독립성, 범위 정규화, clamp, 추가 상환 제약과 3개 golden case를 자동 테스트로 고정했다.
- 다음 단계는 7. Goal Reverse Simulation이며 시나리오 저장과 자연어 처리는 아직 범위 밖이다.

## 7. Goal Reverse Simulation

- 상태: TARGET_NET_WORTH MVP 완료.
- 목표: 목표 금액·기한에서 필요한 지출 절감, 소득 증가, 절감 후 투자 조건을 역산한다.
- 주요 클래스: `FinancialGoal`, `GoalReverseSolver`, `GoalPlan`, `GoalSolverResult`, `GoalReverseSimulationService`.
- API: `POST /api/goals/reverse-simulate`가 최신 Profile을 사용해 baseline과 고정 순서의 세 대안을 반환한다.
- 알고리즘: 기존 `MonthlyFinancialSimulationEngine`을 Oracle로 사용하며 지수적 상한 탐색 후 1원 단위 `BigDecimal` 이진 탐색과 solution/solution-1 경계 검증을 수행한다. 대안당 최대 128회다.
- 지출 상한: 기간 중 baseline 변동지출의 최솟값을 넘지 않아 고정지출까지 자동 절감하지 않는다.
- 완료 조건: 가능/부분 가능/불가능, 최소 요구 조건, 구조화 경고와 전체 월별 결과를 결정론적으로 설명하며 Goal과 결과를 저장하지 않는다.
- 테스트: 소득·지출·투자 골든 경계, 이미 달성 가능, 불가능 상한, 최신 Profile 불변, Validation, 인증, 반복 제한과 기존 baseline 동일성.

## 8. 거래 CSV 및 Pattern Engine

- 상태: 완료. 인증된 `POST /api/patterns/analyze-csv`에서 최대 2 MiB·10,000행·60개월의 UTF-8 표준 CSV를 요청 메모리에서만 처리한다.
- 구현: `TransactionCsvParser`, `NormalizedTransaction`, `FinancialPatternEngine`, `FinancialPatternAnalysisService`, `FinancialPatternController`, `FinancialPatternAnalysisResponse`.
- 계산: 월별 cash flow, 평균·카테고리·반복거래·고정/변동지출 추정·MAD 변동성·소비 추세·적자 월·Profile 초안과 최신 버전 차이를 `BigDecimal` 결정론으로 계산한다.
- 개인정보 경계: 거래 Entity/Repository, 원문 저장, 금융 원문 로깅, 외부 AI/API 호출 및 Profile 자동 변경을 두지 않는다.
- 완료 조건/테스트: RFC4180·BOM·헤더·크기·행·인코딩·기간·식별자 검증, 계산 골든 케이스, 입력 순서 독립성, 최신 Profile 비교/불변성, 인증 차단을 자동 테스트한다. 상세 계약은 `docs/PATTERN_ANALYSIS_API.md`에 기록한다.

- 목표: 로컬 CSV를 검증·정규화하고 소비/소득 패턴을 내부에서 추출한다.
- 주요 클래스: `TransactionCsvParser`, `Transaction`, `CategorySummary`, `PatternEngine`.
- 완료 조건: 원문은 내부 저장 경계를 넘지 않고 중복·오류 행 처리 정책이 있다.
- 테스트: 인코딩·날짜·금액, 중복, 누락 열, 대용량, 월별 패턴.

## 9. Privacy Boundary

- 상태: Strict Privacy Boundary MVP 완료. 실제 외부 AI 호출 없이 PII 선차단, 금융값 Reference 토큰화, 요청 범위 Vault, 강타입 Payload allowlist와 Preview API를 구현했다.
- 구현: `FinancialValueTokenizer`, `KoreanMoneyParser`, `FinancialReferenceVault`, `PersonalIdentifierDetector`, `OutboundPayloadGuard`, `ExternalAiDraftValidator`, `ReferenceRehydrator`, `ExternalAiGateway` 계약.
- 외부 경계: Gateway는 `ExternalAiScenarioRequest`만 받을 수 있으며 Profile·거래·Pattern·Simulation·Goal·사용자 ID·Vault 타입을 받을 수 없다. 실제 Gateway Bean, SDK와 네트워크 호출은 없다.
- Fail Closed: Draft 하나의 intent·이벤트·참조·필드라도 계약을 위반하면 전체를 거절하고, 검증된 Reference만 기존 `FinancialEventMapper`로 재결합한다.
- 완료 조건/테스트: 한국어 금액·비율·기간·날짜, PII, Reflection allowlist, Vault 비노출, Draft 전체 실패, Event Mapper 재검증, Preview SAFE/BLOCKED, 원문 비노출과 인증 차단을 자동 테스트한다. 상세 계약은 `docs/PRIVACY_BOUNDARY.md`에 기록한다.

- 목표: 외부 전송 가능 데이터의 명시적 허용 목록과 비식별 변환을 강제한다.
- 주요 클래스: `PrivacyPolicy`, `AiSafeFinancialSummary`, `DataExportGuard`.
- 완료 조건: 거래 원문·계좌번호·기관명·식별자는 AI Adapter에 도달할 수 없다.
- 테스트: 금지 필드 차단, 허용 DTO 직렬화, 로그 마스킹, 회귀 테스트.

## 10. 제한적 FinTwin Agent

- 상태: 결정론적 Orchestrator MVP 완료.
- 목표: 구조화 Intent를 고정 Allowlist로 라우팅하고 누락값을 확인한 뒤 기존 결정론적 서비스 하나만 실행한다.
- 주요 클래스: `FinTwinAgentOrchestrator`, `DeterministicIntentRouter`, `InformationGapChecker`, 세 Agent Tool, `DeterministicRiskChecker`, `RuleBasedExplanationComposer`.
- API: `POST /api/agent/execute`가 Baseline, What-if, Goal Reverse Intent와 강타입 결과, 규칙 기반 Risk/설명, 비민감 실행 Trace를 반환한다.
- 실행 제한: Primary Tool은 요청당 최대 1회이며 재시도, Tool chaining, Agent loop, 병렬 실행과 자동 대안 Tool이 없다.
- Privacy 연계: 검증과 Reference 재결합이 끝난 `ExternalAiScenarioDraft`만 Privacy 패키지의 Factory를 거쳐 What-if Command가 될 수 있다. Agent는 Vault, Gateway, 외부 AI SDK와 HTTP Client에 접근하지 않는다.
- 완료 조건: Agent는 Repository/Entity와 금융 계산 엔진에 직접 접근하지 않고 기존 Service 경계를 재사용하며, 누락값을 추정하지 않고 외부 AI 호출 없이 완료·누락·거부·실패 상태를 결정론적으로 반환한다.
- 테스트: Intent/Tool 고정 매핑, 누락·충돌, 부채 상환액, 서비스 1회 호출, 상태 전이, 실패 일반화, Risk/Explanation 근거, Trace 비민감성, Privacy Draft 계약, 인증·Validation과 정적 금칙 검사.
- 다음 범위: 외부 AI Adapter는 아직 구현하지 않으며, 구현 전 outbound DTO allowlist와 Provider timeout/fail-closed 정책을 별도 확정한다.

## 11. AI Adapter

- 상태: Privacy-safe OpenAI 이벤트 구조화 MVP 완료.
- 목표: 외부 AI를 자연어 What-if 이벤트 초안 구조화에만 선택적으로 사용하고 실제 금융 계산은 로컬 엔진에 유지한다.
- 주요 클래스: `OpenAiExternalAiGateway`, `OpenAiProperties`, `OpenAiResponseParser`, `NaturalLanguageWhatIfService`, `NaturalLanguageWhatIfController`.
- 완료 조건: `ExternalAiScenarioRequest` 8개 필드만 Responses API로 전송하고 `store=false`, strict Structured Output, Provider 1회 호출, 자동 retry/fallback 금지와 fail-closed 오류 정책을 지킨다. Draft는 기존 Validator·Reference 재결합·Event Mapper를 통과한 뒤에만 Agent로 전달한다.
- 테스트: 설정 Validation, outbound allowlist, PII/Guard 0회 호출, prompt injection, Structured Output strict 파싱, Reference 재결합/타입, 누락정보, HTTP/timeout/refusal/incomplete/크기 오류, 인증/CSRF, Tool 0/1회, 정적 개인정보 의존성 검사.
- 상세 계약: `docs/OPENAI_ADAPTER.md`.

## 12. Spring Security·Google·Kakao 로그인

- 상태: 단일 인스턴스 세션 기반 Google·Kakao OAuth2 Login MVP 완료.
- 목표: 공식 Spring Security OAuth2 Client로 Provider 인증을 처리하고 최소 Provider Subject를 내부 User와 분리한다.
- 주요 클래스: `OAuthIdentity`, `OAuthIdentityProvisioningService`, `FinTwinPrincipal`, `FinTwinOAuth2UserService`, `FinTwinOidcUserService`, `SecurityContextCurrentUserIdProvider`, `AuthController`.
- 완료 조건: 임시 사용자 fallback 없이 실제 Principal의 내부 User ID만 사용하고, 이메일 자동 병합·Provider Token/응답 저장·JWT를 두지 않는다. 성공/실패 Redirect는 설정된 Frontend Origin으로 고정하며 CSRF, credentialed CORS, HttpOnly/SameSite/Secure 쿠키 정책을 유지한다.
- 테스트: Google/Kakao 최소 Attribute 매핑, 최초/재로그인, Provider 분리, 동시 생성 UNIQUE, Principal 강제, 인증 위조 차단, 고정 Redirect, CSRF/me/logout, CORS, session fixation 설정, Profile·Simulation·Goal·Agent 사용자 분리.
- 운영 문서: `docs/OAUTH2_LOGIN.md`, `docs/sql/oauth_identity_mysql8.sql`.

## 13. React JavaScript 프론트엔드

- 목표: 별도 React JavaScript 앱에서 Profile과 시나리오 흐름을 제공한다.
- 주요 클래스: JavaScript API client, Profile form, Scenario editor, Comparison view.
- 완료 조건: TypeScript 없이 Validation 오류와 인증 상태를 안전하게 처리한다.
- 테스트: 컴포넌트, API mocking, 접근성, 주요 사용자 E2E 흐름.

## 14. Docker

- 목표: Java 21 Temurin 기반 재현 가능한 백엔드 이미지를 만든다.
- 주요 클래스: `Dockerfile`, `.dockerignore`, 로컬 compose 설정(필요 시 MySQL만).
- 완료 조건: secret이 이미지에 포함되지 않고 non-root 실행과 health check가 동작한다.
- 테스트: 이미지 빌드, 컨테이너 health, 환경변수 누락 실패, graceful shutdown.

## 15. AWS 배포

- 목표: EC2와 RDS MySQL 8에 최소 권한·암호화·백업 기준으로 배포한다.
- 주요 클래스: 배포 스크립트/문서, prod 환경 설정, 스키마 적용 절차.
- 완료 조건: RDS 비공개 접근, TLS, Secret 주입, `ddl-auto=validate`, 복구 절차가 확인된다.
- 테스트: staging smoke/health, DB 연결, 롤백, 백업 복원, 보안 그룹 점검.

## 16. 대회 제출 QA

- 목표: 기능·수치 정확성·privacy·데모 복원력을 최종 검증한다.
- 주요 클래스: QA 체크리스트, 데모 seed/시나리오, 제출 문서와 아키텍처 설명.
- 완료 조건: 전체 테스트·골든 테스트·보안 점검이 통과하고 미구현/제약이 명확하다.
- 테스트: clean build, 핵심 E2E, 장애 fallback, 민감정보 검색, 새 환경 재현.
