# FinTwin 작업 지침

## 실제 기술 스택

- Java Toolchain 21, Spring Boot 4.1.0, Spring Framework 7/Spring Security 7 계열
- Gradle 9.5.1 Wrapper, Groovy DSL
- Spring Web MVC, Spring Data JPA, Validation, Security, OAuth2 Client, Actuator
- MySQL 8(운영/로컬), H2(테스트 전용), Lombok
- 기본 패키지: `com.fintwin.fintwin`

## 명령

- 로컬 실행: `./gradlew bootRun --args='--spring.profiles.active=local'` (Windows: `gradlew.bat`)
- 전체 테스트: `./gradlew test`
- 검증 빌드: `./gradlew clean build`

## 설계 및 보안 규칙

- `global`, `auth`, `user`, `financialprofile` 등 package-by-feature를 사용한다. 미구현 기능의 빈 클래스를 만들지 않는다.
- 모든 금액과 이율은 `BigDecimal`을 사용하고 스케일·반올림 정책을 테스트한다. `double`/`float`을 금지한다.
- 시뮬레이션·목표·최적화·위험 계산 엔진은 순수 Java로 유지하고 Spring, JPA, Controller, AI SDK에 의존하지 않는다.
- 엔티티를 API에 직접 반환하지 않고 요청/응답 DTO와 분리한다.
- 금융 원문, 거래내역, 계좌번호, 금융기관명은 외부 AI에 보내지 않는다. AI 계층은 Repository/Entity에 직접 접근하지 않는다.
- External AI integrations must depend only on explicit privacy-safe outbound DTOs.
- FinancialProfile, transactions, patterns, simulation results, user identifiers, and request-scoped reference vaults must never cross the External AI Gateway boundary.
- OpenAI Adapter는 `ExternalAiScenarioRequest`만 입력받고 Responses API를 요청당 최대 1회 호출한다. `store=false`를 유지하며 tool, streaming, retry, 모델 fallback, 대화 상태를 추가하지 않는다.
- Provider 응답은 untrusted draft로 간주하고 strict 파싱, `ExternalAiDraftValidator`, Reference 재결합, `FinancialEventMapper` 검증을 모두 통과시킨다.
- AI 설정은 기본 비활성화하며 실제 API Key는 환경변수 또는 운영 Secret 저장소에서만 주입한다. 원문 요청·Provider 응답·Vault를 DB, Trace, 로그에 저장하지 않는다.
- 비밀번호, OAuth/JWT Secret, DB 접속정보를 코드·설정·Git에 하드코딩하지 않는다. 민감정보를 로그에 남기지 않는다.
- 새 라이브러리는 필요성과 기존 대안을 먼저 확인하며, 현재 필요 없는 인프라는 추가하지 않는다.
- 변경 후 관련 테스트와 전체 `./gradlew test`를 실행한다.

## 완료 조건

- 실제 Boot 4.1/Security 7 API와 호환되고 전체 테스트가 통과한다.
- DTO Validation, 예외 응답, 프로필별 DB 정책과 privacy boundary가 유지된다.
- 비밀값·금융 원문 노출과 엔티티 직접 반환이 없고, 변경 범위 및 미구현 사항이 문서화된다.
- AI 변경은 실제 외부 네트워크 없이 Mock 서버로 검증하고 개인정보 비노출 정적 검사와 전체 테스트를 통과한다.
