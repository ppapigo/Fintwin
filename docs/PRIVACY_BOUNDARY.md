# Strict Privacy Boundary

## 목적과 STRICT 정책

FinTwin의 외부 AI는 사용자의 금융 상태를 계산하거나 분석하지 않는다. 외부 AI의 허용 역할은 사용자가 직접 입력한 자연어 시나리오에서 의도, 지원 이벤트 유형, 상대 날짜 표현과 금융값 Reference ID를 구조화하는 것뿐이다.

기본이자 유일한 개인정보 정책은 `STRICT`다. 서버 내부의 Financial Profile, Pattern Report, 거래, 시뮬레이션, Goal 결과와 사용자 식별자는 외부 AI 요청 타입으로 변환되지 않는다. 계산과 검증은 기존 결정론적 서버 엔진이 담당한다.

## 데이터 흐름

```text
사용자 시나리오 텍스트
  -> 로컬 입력 검증과 PII 탐지
  -> 로컬 금융값 토큰화
  -> 요청 범위 FinancialReferenceVault
  -> 강타입 ExternalAiScenarioRequest allowlist 검사
  -> 향후 ExternalAiGateway
  -> 강타입 ExternalAiScenarioDraft 전체 검증
  -> 검증된 Reference ID 단건 재결합
  -> 기존 FinancialEventMapper 검증
  -> 기존 결정론적 Simulation Engine
```

현재 단계에는 실제 External AI Gateway Bean, OpenAI SDK, HTTP Client, API Key, Prompt 및 네트워크 호출이 없다.

## 외부 AI 허용 데이터

`ExternalAiScenarioRequest`는 다음 8개 필드만 가진 불변 record다.

- `schemaVersion`: `1`
- `purpose`: `SCENARIO_EVENT_EXTRACTION`
- `locale`: `ko-KR`
- `currentYearMonth`
- `sanitizedScenarioText`
- `supportedEventTypes`
- `supportedReferenceTypes`
- `outputContractVersion`: `1`

지원 이벤트는 `ONE_TIME_EXPENSE`, `RECURRING_EXPENSE_CHANGE`, `INCOME_CHANGE`, `INCOME_PAUSE`, `INVESTMENT_CONTRIBUTION_CHANGE`, `EXTRA_DEBT_REPAYMENT`으로 고정된다.

## 외부 AI 금지 데이터

다음 정보는 Gateway 메서드 인자와 외부 Payload에 포함할 수 없다.

- User ID, 이메일, OAuth Provider ID
- Financial Profile ID·version·Entity·DTO와 모든 실제 Profile 금액
- CSV 원문, `NormalizedTransaction`, 거래 description 원문
- Pattern Report, Profile Draft
- Baseline·What-if Simulation 및 Goal Solver 결과
- 월별 자산·부채·순자산·현금흐름
- 계좌·카드·주민등록·전화·주소·실명
- Token Vault와 실제 Reference 값
- API Key, Secret 및 내부 설정

사용자 시나리오에 포함된 HTML·Markdown·prompt-injection 형태의 문장은 실행하거나 시스템 지시로 취급하지 않고 일반 텍스트로만 토큰화한다. 이 텍스트가 요청 DTO의 필드 구조나 상수 allowlist를 바꿀 수 없다.

## 금융값 토큰화

지원 Reference 유형은 `MONEY`, `PERCENT`, `DURATION`, `DATE`다. 금융 표현은 원문 위치 순서에 따라 유형별로 결정론적인 ID를 부여한다.

```text
내년에 3천만원짜리 자동차를 사면?
-> 내년에 [MONEY_1]짜리 자동차를 사면?

월 생활비를 20만원 줄이고 월 50만원을 투자하면?
-> 월 생활비를 [MONEY_1] 줄이고 월 [MONEY_2]을 투자하면?

대출금리가 2.5% 오르면?
-> 대출금리가 [PERCENT_1] 오르면?
```

한국어 금액 `3천만원`, `3000만원`, `1억원`, `1억 5천만원`, `50만원`, `500만원`, 쉼표 금액과 일반 원 단위를 `BigDecimal`로 정규화한다. 기간은 월 수 정수, 절대 날짜는 `YearMonth`로 보관한다. `내년`, `다음 달`, `3개월 뒤` 같은 상대 날짜는 임의의 절대 날짜로 토큰화하지 않고 의도 표현으로 남긴다.

날짜가 `2027년 3월`처럼 기간 표현과 겹칠 수 있을 때는 PII → 날짜 → 금액 → 비율 → 기간 우선순위를 사용하며, 이미 생성된 토큰을 다시 토큰화하지 않는다.

## 요청 범위 Token Vault

실제 금액과 날짜는 `FinancialReferenceVault`의 private Map에만 존재한다. Vault는 매 요청마다 지역 객체로 생성되고 다음 저장소나 경계로 전달되지 않는다.

- DB, Redis, Cache, HTTP Session, 파일
- static 필드, 전역 Map, 비동기 Queue
- API 응답, 외부 Gateway, 로그

전체 Map 조회 API는 없고 검증·재결합에 필요한 Reference ID 단건 조회만 제공한다. `toString()`에는 요청 범위라는 표시와 참조 개수만 포함하며 실제 값은 포함하지 않는다. 기본 identity 기반 equals/hashCode를 사용한다.

## PII 선차단

주민등록번호, 이메일, 국내 전화번호, 13~19자리 카드번호, 하이픈 그룹 계좌번호, API Key·Secret 형태, 20자리 이상 연속 숫자, NUL·제어문자를 토큰화 전에 검사한다.

PII가 하나라도 감지되면 전체 요청은 `BLOCKED`가 되고 외부 Payload와 Reference 목록을 만들지 않는다. 응답은 탐지된 정보의 유형만 반환하며 감지된 문자열을 포함하지 않는다. 부분 마스킹 후 외부로 전송하는 방식은 사용하지 않는다.

이름과 주소는 정규식만으로 안정적으로 판별할 수 없다. 따라서 Preview 입력에는 이름·주소·계좌 정보를 넣지 말아야 한다는 UI 안내가 필요하며, 향후 별도의 정교한 로컬 탐지 정책 전까지 이 한계를 보안 보장으로 과장하지 않는다.

## Payload Allowlist

`OutboundPayloadGuard`는 문자열 금칙어 검색만 사용하지 않는다. 우선 실제 record 타입과 정확한 8개 필드 이름, Map 부재, 상수, 지원 Enum 목록을 Reflection으로 검증한다. 이어 sanitized text에 PII 또는 아직 토큰화되지 않은 금융값이 남아 있는지 검사한다.

`supportedEventTypes`의 `INCOME_CHANGE`처럼 정상 스키마에 포함된 단어는 민감 필드로 오판하지 않는다. `userId` 같은 임의 필드를 가진 다른 record나 Map은 타입 단계에서 거절된다.

## 외부 AI 응답 불신과 Fail Closed

`ExternalAiScenarioDraft`는 실제 금액 타입을 가지지 않는다. 이벤트는 Reference ID, 지원 event type, 상대 날짜 표현 또는 DATE Reference, 증감 방향, 비민감 description만 표현한다.

다음 중 하나라도 실패하면 정상 이벤트까지 포함한 전체 Draft를 거절한다.

- 지원하지 않는 intent 또는 event type
- 중복 event ID, 20개 초과 이벤트
- 잘못된 Reference ID 형식
- 현재 요청 Vault에 없는 Reference
- MONEY 필드에서 PERCENT 등 잘못된 Reference 타입 사용
- 이벤트 유형에 필요하지 않은 필드 존재
- AI가 description에 직접 생성한 금융값 또는 PII
- 지원하지 않는 상대 날짜 표현
- description·missing-field 계약 위반

External AI 출력은 자동 실행되지 않는다.

## 로컬 재결합

검증이 끝난 Reference ID만 Vault에서 단건 조회한다. `DECREASE` 방향은 서버에서 금액 부호로 변환하고, 상대 날짜는 요청의 기준 연월로 서버에서 계산한다. 이후 기존 `FinancialEventMapper`를 재사용해 이벤트 수, 금액, 기간, 시뮬레이션 범위를 다시 검증한다.

외부 AI가 반환한 숫자를 계산 엔진이 직접 사용하거나, 기존 이벤트 검증을 복제·우회하지 않는다.

## Preview API

```http
POST /api/privacy/scenario-payload-preview
Content-Type: application/json
```

인증과 현재 Spring Security의 CSRF 정책이 적용된다.

```json
{
  "scenarioText": "내년에 3천만원짜리 자동차를 사면?"
}
```

SAFE 응답 예시:

```json
{
  "status": "SAFE",
  "externalPayload": {
    "schemaVersion": "1",
    "purpose": "SCENARIO_EVENT_EXTRACTION",
    "locale": "ko-KR",
    "currentYearMonth": "2026-08",
    "sanitizedScenarioText": "내년에 [MONEY_1]짜리 자동차를 사면?",
    "supportedEventTypes": [
      "ONE_TIME_EXPENSE",
      "RECURRING_EXPENSE_CHANGE",
      "INCOME_CHANGE",
      "INCOME_PAUSE",
      "INVESTMENT_CONTRIBUTION_CHANGE",
      "EXTRA_DEBT_REPAYMENT"
    ],
    "supportedReferenceTypes": ["MONEY", "PERCENT", "DURATION", "DATE"],
    "outputContractVersion": "1"
  },
  "references": [{"referenceId": "MONEY_1", "referenceType": "MONEY"}],
  "blockedIdentifierTypes": [],
  "privacyNotice": "Exact financial values and user financial profiles are not included in the external AI payload."
}
```

BLOCKED 응답은 `externalPayload: null`, 빈 `references`, 탐지 유형만 포함한다. SAFE와 BLOCKED 응답 모두 원문 scenario text와 Vault 값을 echo하지 않는다.

## 감사 정보와 저장

현재 단계에서는 감사 정보를 DB에 저장하지 않는다. 정책 버전, 목적, SAFE/BLOCKED, Reference 수, PII 유형, 처리 시각 같은 비금융 메타데이터만 향후 요청 범위에서 생성할 수 있다. 원문, sanitized text, Vault, 실제 금융값, 사용자 금융정보와 AI 응답 원문은 감사 데이터가 될 수 없다.

## 향후 External AI Adapter 연결 조건

향후 Adapter는 오직 `ExternalAiGateway`를 구현하고 `ExternalAiScenarioRequest`만 받아야 한다. 응답 JSON은 알 수 없는 필드를 거절하는 엄격한 역직렬화 후 `ExternalAiDraftValidator`를 통과해야 한다. Vault와 Profile·Pattern·Simulation·Repository는 Adapter 생성자와 메서드에 추가할 수 없다.
