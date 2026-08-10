# OpenAI Provider 자연어 What-if E2E 검증

## 검증 범위

- 검증일: 2026-08-10 (Asia/Seoul)
- Provider / Model: OpenAI / `gpt-5.6-luna`
- API: Responses API + Structured Outputs
- Provider 저장 설정: `store=false`
- 공통 계산 조건: 시작 연월 2026-08, 기간 60개월, 화면에 설정된 동일 Assumption, Financial Profile v2
- Provider Tool/Function Calling: 사용하지 않음
- FinTwin 로컬 Agent Tool: 성공한 자연어 실행마다 Scenario Tool 1회

API Key, Provider 원문 요청·응답, Provider Request ID, Session ID, OAuth Token, Vault, Reference 실제값 대응표는 조회하거나 기록하지 않았다.

## 사전 검증

- Backend 기준선: 263 tests, 실패 0
- Frontend 기준선: 56 tests, 실패 0
- Backend `clean build` 성공
- Frontend test/build 성공
- MySQL 컨테이너와 Backend `/actuator/health`가 정상인 상태에서 사용자가 직접 Google OAuth 로그인을 완료했다.

## PII 차단

합성 이메일 패턴이 포함된 문장을 Privacy Preview에 입력했다.

- Preview 결과: `BLOCKED`
- 외부 Provider 호출: 0회
- 로컬 Agent Tool 호출: 0회
- 실행 버튼: 비활성
- 오류 영역에 원문 이메일 문자열을 재표시하지 않음

## 실제 Provider 시나리오

### 1. 일회성 지출

- Preview: `SAFE`; 날짜와 금액은 각각 `DATE`, `MONEY` Reference로 토큰화
- 최종 정규화 이벤트: `ONE_TIME_EXPENSE`, 적용 월 2026-09, 금액 1,000,000원
- 최종 성공 실행: Provider 1회, 로컬 Agent Tool 1회, `COMPLETED`
- 최종 순자산 Delta: -1,000,000원
- 월별 Baseline/What-if 60개월, 1·3·5년 Checkpoint, Final Comparison, Impact Summary, Warning, Profile Version 존재 확인

초기 빌드에서는 Provider가 반환한 이벤트 유형과 필드 조합이 로컬 검증 계약을 통과하지 못해 `AI_SCHEMA_VIOLATION`으로 안전하게 중단됐다. 수정 후 새 빌드에서 한 번 재검증했으며 자동 재시도는 사용하지 않았다.

### 2. 반복 지출 감소

- Preview: `SAFE`; 두 날짜와 금액을 Reference로 토큰화
- 최종 정규화 이벤트: `RECURRING_EXPENSE_CHANGE`, 2026-09부터 2027-02까지 종료 월 포함, 월 -100,000원
- Provider 1회, 로컬 Agent Tool 1회, `COMPLETED`
- 소비 Delta: -600,000원
- 최종 순자산 Delta: +600,000원
- 월별 Baseline/What-if 60개월, 1·3·5년 Checkpoint, Final Comparison, Impact Summary, Warning, Profile Version 존재 확인

### 3. 소득 중단

- Preview: `SAFE`; 시작 날짜와 기간을 각각 `DATE`, `DURATION` Reference로 토큰화
- 최종 정규화 이벤트: `INCOME_PAUSE`, 2027-01부터 2027-03까지 종료 월 포함
- AI가 소득 금액을 생성하지 않았으며 계산은 로컬 Profile의 월 소득을 사용
- 최종 성공 실행: Provider 1회, 로컬 Agent Tool 1회, `COMPLETED`
- 누적 소득 Delta: -9,000,000원
- 최종 순자산 Delta: -9,000,000원

초기 빌드에서는 일회성 지출과 같은 계약 불일치로 `AI_SCHEMA_VIOLATION`이 발생했다. 수정 후 새 빌드에서 한 번 재검증했으며 자동 재시도는 사용하지 않았다.

## 자연어와 직접 입력 결과 일치

Provider가 만든 정규화 이벤트를 동일 Assumption과 Profile v2로 직접 입력해 다시 실행했다.

| 시나리오 | Normalized Event | 60개월 Series | Checkpoint | Final | Impact | Warning | Profile Version |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 일회성 지출 | 일치 | 일치 | 일치 | 일치 | 일치 | 일치 | 일치 |
| 반복 지출 감소 | 일치 | 동일 이벤트가 같은 결정론적 Scenario Tool 경로로 실행됨 | 화면 대조 일치 | 화면 대조 일치 | 화면 대조 일치 | 일치 | 일치 |
| 소득 중단 | 일치 | 일치 | 일치 | 일치 | 일치 | 일치 | 일치 |

일회성 지출과 소득 중단은 펼친 월별 60개 행의 전체 텍스트를 직접 비교했다. 반복 지출 감소는 실제 자연어 실행에서 전체 Series가 수신·렌더링됐고, 직접 입력의 전체 Series를 확인했다. 또한 `NaturalLanguageScenarioComparisonIntegrationTest`가 동일 정규화 이벤트의 자연어·직접 입력 `ScenarioComparisonDetails` 전체를 재귀 비교한다.

## 발견한 결함과 수정

기존 Structured Output Schema는 하나의 이벤트 객체에 모든 이벤트 유형의 필드를 허용했다. 따라서 JSON 구조는 유효해도 `ONE_TIME_EXPENSE` 또는 `INCOME_PAUSE`에 적용할 수 없는 필드 조합이 생성될 수 있었고, 로컬 Privacy 검증기가 이를 올바르게 거부했다.

수정 내용:

- 이벤트 항목을 중첩 `anyOf`로 분리
- 일회성, 반복 변경, 소득 중단, 이벤트 유형 미확정 형태별 허용 필드를 Schema에서 제한
- 적용하지 않는 필드는 `null`만 허용
- 금액·날짜·기간 Reference의 기존 패턴과 `additionalProperties=false` 유지
- 이벤트 유형별 필드 제약을 확인하는 회귀 테스트 추가

Prompt를 길게 늘리거나 Validator를 느슨하게 만들지 않았고, Provider 원문 응답을 저장·출력하지 않았다.

## 호출 횟수와 오류

- 최종 성공 검증: 각 시나리오 Provider 1회, 로컬 Scenario Tool 1회
- 결함 발견부터 수정 후 확인까지: 일회성 지출 2회, 반복 지출 감소 1회, 소득 중단 2회
- 초기 실패 2건은 Provider 호출 후 로컬 계약 검증에서 중단되어 Tool 0회
- 자동 Retry 0회, Model Fallback 0회
- 인증, Rate Limit, Timeout, Provider 5xx 오류는 발생하지 않음

## 저장·Privacy 경계 확인

- OpenAI 요청은 `store=false`
- Provider Request에 `tools`, `previous_response_id`, 사용자 ID, Profile ID를 넣지 않음
- 실제 금융값, Financial Profile, 사용자 식별자, Reference 대응표를 외부 Payload에 넣지 않음
- Scenario 결과와 Provider 요청·응답을 DB에 저장하지 않음
- 자연어 문장이나 결과를 Local Storage, Session Storage, URL Query에 저장하지 않음
- 금융 계산, 위험 판정, 비교 결과 생성은 로컬 결정론적 엔진에서 수행
- Provider 원문 응답을 로그에 기록하지 않음

## 미검증 항목

- OpenAI Dashboard의 요청 상세와 사용량은 자동 조회하지 않음
- 실제 401, 403, 429, Timeout, 5xx를 Provider에 의도적으로 발생시키지 않음
- Kakao OAuth는 이번 범위에서 실행하지 않음

## 참고

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI Responses API migration guide](https://developers.openai.com/api/docs/guides/migrate-to-responses)
