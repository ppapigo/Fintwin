# Privacy-first What-if Frontend

`/what-if`는 인증된 사용자의 최신 Financial Profile을 기준으로 Baseline과 What-if를 비교한다. Profile이 없으면 `/profile/setup`으로 이동하며 요청에 User ID나 Profile ID를 넣지 않는다. 결과와 입력은 서버 저장 API, 브라우저 저장소, URL Query, Console 또는 Analytics에 저장·전송하지 않는다.

## 입력 흐름

### 자연어

```text
문장 입력
→ POST /api/privacy/scenario-payload-preview
→ SAFE 토큰화 Payload 확인 및 사용자 승인
→ POST /api/agent/natural-language
→ 로컬 검증·Reference 재결합
→ 결정론적 ScenarioSimulationService / MonthlyFinancialSimulationEngine
→ 규칙 기반 Risk·Explanation과 요약 결과
```

Preview가 `BLOCKED`이면 외부 AI 호출과 실행을 막고 PII 유형만 표시한다. `SAFE`이면 Privacy Mode, 토큰화 문장, Reference Type, 외부 전송 필드 이름만 표시한다. Reference ID, 실제 금융값, Reference 대응표는 표시하지 않는다. 문장을 수정하면 Preview와 승인을 즉시 폐기한다.

외부 AI로 전달 가능한 Preview 필드는 Backend `ExternalAiScenarioRequest`의 allowlist와 동일한 다음 8개다.

- `schemaVersion`
- `purpose`
- `locale`
- `currentYearMonth`
- `sanitizedScenarioText`
- `supportedEventTypes`
- `supportedReferenceTypes`
- `outputContractVersion`

AI는 자연어 이벤트 구조화에만 사용된다. 금융 계산, 위험 판정, 순자산 비교는 외부 AI가 수행하지 않는다. `COMPLETED`, `NEEDS_INPUT`, `REJECTED`, `FAILED`를 구분하며 누락 조건을 임의 기본값으로 채우지 않는다. Provider 원문 오류는 표시하지 않는다.

### 직접 입력 fallback

직접 입력은 `POST /api/simulations/compare`만 호출하고 외부 AI를 호출하지 않는다. `ONE_TIME_EXPENSE`, `RECURRING_EXPENSE_CHANGE`, `INCOME_CHANGE`, `INCOME_PAUSE`, `INVESTMENT_CONTRIBUTION_CHANGE`, `EXTRA_DEBT_REPAYMENT` 6종을 지원한다. 이벤트는 최대 20개이며 이벤트 유형에 필요한 필드만 전송한다. Event ID는 사용자정보 없는 브라우저 내부 식별자다.

OpenAI가 비활성화되거나 timeout, rate limit, refusal, schema 위반이 발생해도 이 경로는 독립적으로 동작한다. 자연어와 직접 입력 모두 최종적으로 같은 로컬 Scenario Simulation 엔진을 사용한다.

## 공통 Assumption과 결과 Mapping

`SimulationAssumptionFields`와 `simulationAssumptions.js`를 `/twin`과 `/what-if`가 공유한다. 시작 연월, 12·36·60개월, 네 가지 연율, 월 대출상환액의 상태·검증·요청 변환을 중복 구현하지 않는다.

`scenarioViewModel.js`는 두 응답을 표시용 View Model로 매핑한다. 이 계층은 순자산, Delta, Checkpoint, 영향 기여도를 계산하거나 Backend 오류를 보정하지 않는다. 응답의 내부 Profile ID는 API 정규화 단계에서 제거하고 Profile Version만 표시한다. 직접 Compare 결과는 다음을 표시한다.

- 최종 순자산·유동자산·부채·누적 지표 비교
- 월별 Baseline 점선과 What-if 실선 Chart
- Backend Checkpoint와 Delta
- `impactSummary`와 `residualDelta`
- 정규화 이벤트와 서비스 경고
- 60개월 월별 상세 Table

자연어 Agent 응답의 현재 `ScenarioAgentToolResult`는 최종 요약만 제공하며 월별 Series, Checkpoint, 정규화 Event 상세를 포함하지 않는다. Frontend는 이를 추정하지 않고 해당 제한을 화면에 표시한다. 직접 Compare 월별 DTO에도 월별 Delta가 없으므로 Table은 `API 미제공`으로 표시하고 재계산하지 않는다.

## 오류 처리

- 인증과 CSRF는 기존 Session 기반 API Client를 재사용한다.
- CSRF 실패 요청을 자동 재시도하지 않는다.
- Profile 404는 Profile 설정으로 이동한다.
- Preview 대기·SAFE·BLOCKED와 Agent 네 상태를 분리한다.
- AI 장애는 안전한 사용자 메시지와 직접 입력 이동 버튼을 제공한다.
- 새 요청 전 기존 결과를 제거해 실패한 요청 뒤 이전 결과가 최신처럼 보이지 않게 한다.
- 요청 중 버튼을 비활성화하고 동일 실행의 중복 요청을 막는다.

## 실행과 검증

```powershell
cd frontend
npm install
npm run dev
npm test
npm run build
```

Backend는 별도 터미널에서 `gradlew.bat bootRun --args='--spring.profiles.active=local'`로 실행한다. 로컬 Vite는 `/api`, `/oauth2`, `/login`을 Backend로 proxy한다.

자동 테스트는 Privacy Preview 승인 경계, PII 차단, Agent 상태, AI 비활성화 fallback, 이벤트 계약·범위·개수, ID 비노출, 결과 Mapping을 검증한다. 실제 AI E2E 성공 여부는 `OPENAI_API_KEY`와 Adapter 활성화 상태에 좌우된다. Key가 없거나 Adapter가 비활성화된 검증에서는 자연어 성공을 주장하지 않고 직접 입력 실제 실행과 Mock 계약 테스트를 구분해 보고한다.

## 현재 한계

- 자연어 응답에는 전체 월별 Series·Checkpoint·정규화 Event가 없다.
- 직접 Compare 월별 응답에는 월별 Delta가 없다.
- 결과 저장, CSV 다운로드, Scenario Lab, Goal UI, 상품 추천은 범위 밖이다.
- 자연어 Preview가 SAFE여도 AI Provider 가용성을 보장하지 않는다.
