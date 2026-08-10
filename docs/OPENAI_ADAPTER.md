# Privacy-safe OpenAI Adapter

## 역할과 경계

OpenAI Adapter의 허용 역할은 사용자가 입력한 자연어 What-if 문장에서 지원 이벤트 유형, 상대 날짜 표현, 토큰화된 금융 Reference ID와 누락 필드를 구조화하는 것뿐이다. 금융 계산, Profile 분석, Risk 판단, Goal 역산, 투자수익 예측, 상품 추천, Tool 호출은 금지된다. 실제 계산·비교·위험 판정·설명은 기존 로컬 결정론적 엔진과 `FinTwinAgentOrchestrator`가 수행한다.

```text
사용자 자연어
  -> 로컬 Validation / PII 탐지
  -> 로컬 금융값 토큰화 + 요청 범위 Vault
  -> ExternalAiScenarioRequest allowlist / OutboundPayloadGuard
  -> OpenAI Responses API (1회, store=false)
  -> 강타입 Provider DTO / Strict Structured Output 파싱
  -> ExternalAiDraftValidator
  -> ReferenceRehydrator / FinancialEventMapper
  -> ValidatedScenarioAgentCommandFactory
  -> FinTwinAgentOrchestrator
  -> 로컬 Scenario 비교, Risk, 규칙 기반 설명
```

Vault, 원문, Profile, 거래, Pattern, Simulation·Goal 결과와 사용자 식별자는 OpenAI Adapter로 전달되지 않는다. Provider 요청·응답 원문은 DB, Trace, 로그, API 응답에 저장하거나 복사하지 않는다.

## 실제 외부 전송 필드

Provider `input`에는 `ExternalAiScenarioRequest`의 다음 8개 필드만 JSON 문자열로 들어간다.

- `schemaVersion`
- `purpose`
- `locale`
- `currentYearMonth`
- `sanitizedScenarioText`
- `supportedEventTypes`
- `supportedReferenceTypes`
- `outputContractVersion`

별도 Provider 제어 필드는 `model`, 중앙 관리 `instructions`, `store=false`, `max_output_tokens`, `text.format`뿐이다. Assumption, User/Profile ID, 실제 금융값, 도구, conversation, `previous_response_id`, streaming 설정은 없다.

## Responses API와 Structured Outputs

Adapter는 Spring Framework의 `RestClient`로 `POST /v1/responses`를 한 번만 호출한다. OpenAI SDK와 Spring AI는 사용하지 않는다.

```json
{
  "model": "gpt-5.6-luna",
  "instructions": "중앙 관리된 고정 지시문",
  "input": "ExternalAiScenarioRequest JSON 문자열",
  "store": false,
  "max_output_tokens": 1200,
  "text": {
    "format": {
      "type": "json_schema",
      "name": "fintwin_scenario_draft",
      "strict": true,
      "schema": {}
    }
  }
}
```

Schema는 `src/main/resources/ai/openai/fintwin-scenario-draft.schema.json`에 있다. 모든 object는 `additionalProperties: false`, 이벤트는 최대 20개이며, 이벤트/Reference enum·문자열 길이·Reference ID 패턴을 제한한다. 직접 숫자 금융값 필드는 없다. 필수 정보가 없으면 값을 추정하지 않고 `missingFields`로 반환한다.

고정 지시문은 사용자 텍스트를 명령이 아닌 untrusted data로 취급하고, 토큰 값 복원·금융 계산·조언·Markdown·지원하지 않는 이벤트·임의 기본값 생성을 금지한다.

## 응답 검증과 fail-closed

Provider 외곽 응답은 강타입 DTO로 읽고 정확히 하나의 `message/output_text`만 허용한다. 선택적인 reasoning item 외의 예상하지 못한 output, 복수 message, refusal, incomplete, 빈 output, 잘못된 JSON을 거부한다. Structured Output은 unknown property, trailing token, 숫자→문자열 coercion을 거부하는 전용 strict reader로 다시 읽는다.

파싱 뒤에도 결과를 신뢰하지 않고 다음 순서를 모두 통과시킨다.

```text
ExternalAiScenarioDraft
  -> ExternalAiDraftValidator
  -> ReferenceRehydrator
  -> FinancialEventMapper
```

오류 코드는 다음과 같다.

- `AI_DISABLED`
- `AI_CONFIGURATION_INVALID`
- `AI_TIMEOUT`
- `AI_AUTHENTICATION_FAILED`
- `AI_RATE_LIMITED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_REFUSED`
- `AI_INCOMPLETE_RESPONSE`
- `AI_EMPTY_RESPONSE`
- `AI_RESPONSE_TOO_LARGE`
- `AI_SCHEMA_VIOLATION`
- `AI_PRIVACY_GUARD_REJECTED`
- `AI_UNKNOWN_ERROR`

오류 시 retry, 모델 fallback, 부분 성공, 임의 보정을 하지 않는다. Provider 원문 body, API Key, 사용자 원문, Vault 값, stack trace는 공통 API 오류 응답에 포함하지 않는다. 연결 timeout 기본값은 3초, 읽기 timeout은 15초, 응답 상한은 65,536 bytes다.

## 설정과 Secret 보관

기본값은 `FINTWIN_AI_ENABLED=false`다. 이 상태에서는 API Key 없이 애플리케이션과 테스트가 기동되며 Gateway Bean도 생성되지 않는다. 활성화 상태에서 Key가 비어 있으면 설정 Validation으로 기동을 차단한다.

```powershell
$env:FINTWIN_AI_ENABLED='true'
$env:OPENAI_API_KEY='사용자가 실행 환경에서 직접 설정'
$env:OPENAI_MODEL='gpt-5.6-luna'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

실제 Key는 코드, `application.yaml`, `.env.example`, Git, 로그, 오류 메시지에 넣지 않는다. 배포 시 운영 Secret 저장소 또는 프로세스 환경변수로 주입한다. Base URL은 HTTPS여야 하며 prod 프로필은 `api.openai.com`만 허용한다. HTTP loopback 예외는 test 프로필의 로컬 Mock 서버에만 적용된다.

## 자연어 What-if API

인증 및 기존 CSRF 정책이 적용된 `POST /api/agent/natural-language`를 사용한다.

```json
{
  "scenarioText": "내년에 3천만 원짜리 자동차를 사면?",
  "startYearMonth": "2026-08",
  "horizonMonths": 36,
  "assumptions": {
    "annualIncomeGrowthRate": 2.0,
    "annualInflationRate": 2.0,
    "annualDepositInterestRate": 2.5,
    "annualInvestmentReturnRate": 4.0,
    "monthlyDebtRepayment": 300000
  }
}
```

`scenarioText`만 Privacy Boundary를 거쳐 외부로 나간다. Assumption은 로컬 AgentCommand에만 사용한다. 요청에서 사용자 ID나 Profile ID를 받지 않고 `CurrentUserIdProvider`를 사용한다. 누락 필드가 있으면 `NEEDS_INPUT`, `toolCallCount=0`으로 반환한다.

완료 응답의 `typedResult.comparisonDetails`에는 Profile Version, 적용 Assumption, 정규화 Event, Baseline·What-if 전체 월별 Series, Checkpoint Comparison, Final Comparison, Impact Summary와 계산 Warning이 포함된다. 기존 요약, Risk, 규칙 기반 Explanation, 안전한 Trace와 AI Metadata는 유지한다. 이 확장은 OpenAI Draft Schema 변경이 아닌 Natural Language REST 응답의 additive 변경이므로 별도 전역 버전 체계는 만들지 않았다.

전체 금융 결과는 `ScenarioComparisonTool`의 단 한 번의 `ScenarioSimulationService.compare` 결과에서 복사하며 Adapter나 Controller가 재시뮬레이션하지 않는다. 결과는 인증된 호출자에게만 반환되고 OpenAI 방향으로 역전송되지 않는다. 60개월 응답은 두 월별 Series 때문에 요약 응답보다 커지지만 기간은 최대 60개월, 이벤트는 최대 20개로 제한되며 자동 테스트에서 UTF-8 직렬화 결과가 2 MB 미만임을 검증한다.

수동 테스트는 AI 환경변수 설정 후 현재 애플리케이션의 인증 세션과 CSRF 토큰을 사용해 위 요청을 전송한다. 먼저 `/actuator/health`를 확인하고, Provider 호출 전 개발자 도구나 서버 로그에 원문·금융값·Key를 출력하는 임시 로깅을 추가하지 않는다.

## 현재 한계

- 자연어 실행은 `WHAT_IF_SIMULATION`만 지원한다.
- 이름·주소 PII 탐지는 정규식 기반 로컬 정책의 한계가 있다.
- Provider 응답은 이벤트 초안 생성에만 사용하며 대화 상태를 유지하지 않는다.
- Provider 장애 시 자연어 실행은 fail-closed로 실패하며 자동 fallback은 없다.
- 직접 Compare 월별 DTO가 월별 Delta를 제공하지 않으므로 자연어 상세 응답도 이를 새로 계산하지 않는다.
- 실제 Provider E2E는 `FINTWIN_AI_ENABLED=true`와 실행 환경 Key가 모두 설정된 경우에만 수행한다. 2026-08-10 저장소 검증은 해당 설정이 없어 Test Double 계약 검증만 수행했다.
