# FinTwin 구현 계획

기준 패키지는 `com.fintwin.fintwin`이며 package-by-feature를 사용한다. 계산 코어는 프레임워크와 분리하고, 외부 AI에는 비식별·집계·허용 목록 데이터만 전달한다.

## 1. 기반 설정

- 목표: 프로필별 DB, 오류 응답, CORS, health와 최소 도메인 기반을 확립한다.
- 주요 클래스: `SecurityConfig`, `GlobalExceptionHandler`, `User`, `FinancialProfile`, `FinancialProfileService`.
- 완료 조건: local은 MySQL/update, test는 H2/create-drop, prod는 MySQL/validate이고 비밀값이 환경변수로 주입된다.
- 테스트: Context, `/actuator/health`, 서비스 생성·조회, DTO Validation.

## 2. Financial Profile

- 목표: 사용자 재무상태의 입력·조회·수정과 계산 가능한 스냅샷을 제공한다.
- 주요 클래스: `FinancialProfile`, `FinancialProfileSnapshot`, Profile DTO/Service/Controller.
- 완료 조건: 금액·이율이 `BigDecimal`이고 사용자별 단일 프로필 및 변경 규칙이 보장된다.
- 테스트: CRUD, 경계값, 소유권, 동시 수정, DTO/Entity 매핑.

## 3. 순수 Java 금융 시뮬레이션 엔진

- 목표: 월 단위 현금흐름·자산·부채 변화를 결정론적으로 계산한다.
- 주요 클래스: `MonthlySimulator`, `FinancialState`, `SimulationAssumptions`, `MoneyMath`.
- 완료 조건: 코어 패키지가 Spring/JPA/AI를 import하지 않고 동일 입력에 동일 결과를 낸다.
- 테스트: 이자·상환·저축·투자, 0/큰 값, 반올림·스케일, 불변성.

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

## 7. Goal Reverse Simulation

- 목표: 목표 금액·기한에서 필요한 월 저축/투자/상환 조건을 역산한다.
- 주요 클래스: `FinancialGoal`, `GoalSolver`, `GoalSolution`, `FeasibilityResult`.
- 완료 조건: 가능/불가능과 최소 요구 조건을 결정론적으로 설명한다.
- 테스트: 정확 경계, 불가능 목표, 복수 해, 최대 반복 및 수렴.

## 8. 거래 CSV 및 Pattern Engine

- 목표: 로컬 CSV를 검증·정규화하고 소비/소득 패턴을 내부에서 추출한다.
- 주요 클래스: `TransactionCsvParser`, `Transaction`, `CategorySummary`, `PatternEngine`.
- 완료 조건: 원문은 내부 저장 경계를 넘지 않고 중복·오류 행 처리 정책이 있다.
- 테스트: 인코딩·날짜·금액, 중복, 누락 열, 대용량, 월별 패턴.

## 9. Privacy Boundary

- 목표: 외부 전송 가능 데이터의 명시적 허용 목록과 비식별 변환을 강제한다.
- 주요 클래스: `PrivacyPolicy`, `AiSafeFinancialSummary`, `DataExportGuard`.
- 완료 조건: 거래 원문·계좌번호·기관명·식별자는 AI Adapter에 도달할 수 없다.
- 테스트: 금지 필드 차단, 허용 DTO 직렬화, 로그 마스킹, 회귀 테스트.

## 10. 제한적 FinTwin Agent

- 목표: 내부 도구를 순서화하되 금융 결정을 임의 생성하지 않는 제한적 Agent를 만든다.
- 주요 클래스: `FinTwinAgent`, `AgentIntent`, `AgentPlan`, 허용된 내부 tool handler.
- 완료 조건: Agent는 DTO/서비스 경계만 사용하고 Repository/Entity에 접근하지 않는다.
- 테스트: 도구 허용 목록, 잘못된 호출 차단, 결정론적 fallback, 감사 이벤트.

## 11. AI Adapter

- 목표: 설명·요약에만 외부 AI를 선택적으로 사용하고 공급자를 교체 가능하게 한다.
- 주요 클래스: `AiExplanationPort`, `AiAdapter`, `PromptPolicy`, `AiSafeFinancialSummary`.
- 완료 조건: AI 실패 시 핵심 계산은 유지되고 원문·비밀·Entity가 전송되지 않는다.
- 테스트: 전송 payload 계약, timeout/오류, 민감필드 부재, adapter mock.

## 12. Spring Security·Google·Kakao 로그인

- 목표: OAuth2 로그인과 애플리케이션 사용자 연결, 세션/토큰 정책을 확정한다.
- 주요 클래스: `OAuth2UserService`, `OAuth2LoginSuccessHandler`, `UserPrincipal`, provider mapper.
- 완료 조건: 임시 `CurrentUserIdProvider`가 실제 인증 principal 기반 구현으로 교체되고 secret은 환경변수에만 존재한다.
- 테스트: Google/Kakao 매핑, 신규/기존 사용자, 실패·취소, CSRF/CORS, 접근 제어.

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
