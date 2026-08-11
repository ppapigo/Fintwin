# Goal Reverse Simulation Frontend

`/goal`은 인증된 사용자의 최신 Financial Profile을 기준으로 목표 순자산 달성에 필요한 월 행동 금액을 역산하고, Backend가 반환한 대안을 비교하는 화면이다. 이번 화면은 `TARGET_NET_WORTH`만 지원한다. 결과는 저장하지 않으며 금융수익 또는 목표 달성을 보장하지 않는다.

## 사용자 흐름

```text
로그인
→ 최신 Financial Profile 확인
→ 목표 순자산·시작 연월·12/36/60개월 기간 선택
→ Simulation Assumption 확인
→ POST /api/goals/reverse-simulate
→ 목표 요약
→ Backend 대안 카드·비교 표
→ Baseline과 선택 대안 월별 Chart
→ 경고·Solver 기준·면책 확인
```

Profile이 없으면 계산을 실행하지 않고 `/profile/setup` 이동 동작을 제공한다. 인증이 만료되면 결과나 서버 오류 원문을 표시하지 않고 로그인 화면 이동을 안내한다. Profile Version만 화면에 표시하며 Profile ID는 Profile API 정규화 단계에서 제거된다.

## 요청 Mapping

공통 `SimulationAssumptionFields`와 `simulationAssumptions.js`를 `/twin`, `/what-if`, `/goal`이 공유한다. 목표 종료 월은 별도 금융 계산이 아니라 선택한 기간의 확인용 날짜로만 화면에 표시한다.

```json
{
  "goalType": "TARGET_NET_WORTH",
  "targetAmount": "50000000",
  "startYearMonth": "2026-08",
  "horizonMonths": 60,
  "assumptions": {
    "annualIncomeGrowthRate": "0",
    "annualInflationRate": "0",
    "annualDepositInterestRate": "0",
    "annualInvestmentReturnRate": "0",
    "monthlyDebtPayment": "300000"
  }
}
```

- 금액과 비율은 입력부터 요청까지 문자열로 유지한다.
- 기간 enum만 Backend 계약에 맞춰 숫자 `12`, `36`, `60` 중 하나로 보낸다.
- `userId`, `profileId`, OAuth Subject, Session ID를 본문·URL·상태에 넣지 않는다.
- 기존 공통 API Client의 `credentials: include`와 메모리 CSRF 흐름을 사용한다.
- CSRF 실패 요청을 자동 재시도하지 않는다.
- 실행 중 버튼과 ref 기반 중복 실행 방지를 함께 적용한다.

## 입력 Validation

- 목표 순자산: 0원 초과, 정수 17자리·소수 2자리 이하
- 시작 연월: `YYYY-MM`
- 기간: Backend 지원값인 12·36·60개월
- 목표 종료 월: 시작 월 뒤이며 최대 60개월 범위
- 소득 증가율·물가상승률·투자수익률: -100%~100%, 소수 6자리 이하
- 예금이율: 0%~100%, 소수 6자리 이하
- 월 대출상환액: 0원 이상, 정수 17자리·소수 2자리 이하
- 부채가 있는 Profile: 월 대출상환액 누락 금지
- 지수 표기, `Infinity`, `NaN`, 과도한 자릿수와 소수점 차단

Validation은 요청 편의 기능이며 Backend Validation을 대체하지 않는다. `NEEDS_INPUT`과 공통 Validation 오류는 안전한 안내로 변환하며 서버 오류 body, Stack Trace, 내부 식별자는 화면에 노출하지 않는다.

## 응답 Mapping과 화면 상태

`goalReverseSimulationApi.js`는 응답을 allowlist 형태로 정규화한다. 루트, Baseline, 대안별 `projectedResult`의 `financialProfileId`는 반환 View Model에 포함하지 않고 Profile Version만 유지한다. 금액과 비율은 원본 JSON text에서 문자열로 보존한 뒤 표시한다. Backend Warning의 원문 메시지는 사용하지 않고 Code만 보존해 Frontend의 안전한 문구로 매핑한다.

화면은 초기, Profile 없음, 입력 오류, 실행 중, 성공, 이미 달성 가능, 일부 대안 불가능, 전체 대안 불가능, 인증 만료, 서버 오류를 구분한다. Backend가 반환한 대안만 반환 순서대로 표시하며 다음 Plan을 임의 생성하거나 순위를 계산하지 않는다.

1. `REDUCE_EXPENSE` — 지출 절감
2. `INCREASE_INCOME` — 소득 증가
3. `REDUCE_EXPENSE_AND_INVEST` — 지출 절감 후 투자

각 카드에는 Plan Status, 월 행동 금액, 최종 순자산, 목표 Margin, 구조화 이벤트와 기간, Solver 반복 횟수·상한·제약, 불가능 사유를 표시한다. 비교 표의 최종 자산·부채는 대안의 마지막 월 DTO를 그대로 표시하고, 현금 부족·음의 상환은 반환된 월별 boolean의 존재 여부를 요약한다.

## Warning Mapping

다음 Code에 사용자 친화적인 설명을 제공한다.

- `ALREADY_ACHIEVABLE`
- `EXPENSE_REDUCTION_INFEASIBLE`
- `NEGATIVE_INVESTMENT_RETURN`
- `INVESTMENT_RETURN_BELOW_DEPOSIT_RATE`
- `CASH_SHORTFALL`
- `NEGATIVE_AMORTIZATION`
- `INCOME_INCREASE_EXCEEDS_CURRENT_INCOME`
- `SEARCH_LIMIT_REACHED`
- `INVESTMENT_CONTRIBUTION_CASH_LIMITED`

알 수 없는 Code도 숨기지 않는다. Code와 함께 입력 가정과 대안 상세를 확인하라는 일반 안전 메시지를 표시한다.

## 금융 계산 경계

Frontend는 필요 월 행동 금액, 목표 부족액, 목표 Margin, 최종 자산·부채, 목표 달성 여부를 계산하거나 보정하지 않는다. 같은 의미의 대안을 새로 만들거나 순위를 산출하지도 않는다. Chart는 Backend 월별 결과를 연월로 연결하며 좌표에만 제한적으로 `Number`를 사용한다. Tooltip과 상세 표는 Backend 금액 문자열을 사용하고, 누락된 월이나 필드는 0으로 추정하지 않는다.

결과와 입력은 Local Storage, Session Storage, URL Query, Console, Analytics에 기록하지 않는다. 새 요청 전 이전 결과를 메모리에서 제거하고 새로고침하면 결과도 사라진다.

## Chart

기존 Recharts를 재사용한다. 사용자는 Backend 월별 결과가 있는 대안과 `순자산`, `유동자산`, `투자자산`, `부채` Metric을 선택할 수 있다. Baseline은 점선, 선택 대안은 실선으로 반응형 Chart에 표시한다. 월별 API 값이 없으면 추정 Series를 만들지 않고 데이터 미제공 상태를 표시한다.

## 한계와 면책

- FinTwin은 목표 달성을 보장하지 않는다.
- 투자수익률, 소득증가율, 물가상승률은 사용자 가정이다.
- 세금, 수수료와 실제 소득 증가 가능성은 반영되지 않을 수 있다.
- 계산은 생성형 AI가 아니라 결정론적 금융 엔진이 수행한다.
- 결과는 금융상품 추천이나 투자 자문이 아니다.
- 목표 유형은 현재 `TARGET_NET_WORTH`, 기간은 12·36·60개월만 지원한다.
- 결과 저장, What-if Event 추가, 자연어 목표, 상품 추천은 이 화면의 범위가 아니다.

## 실행과 검증

```powershell
cd frontend
npm test
npm run build

cd ..
.\gradlew.bat test
```

자동 테스트는 Profile 선행 조건, 실제 요청 DTO, ID 미전송, 문자열 정밀도, 날짜·금액 Validation, 중복 제출, 이미 달성 가능한 상태, 세 대안·불가능 대안, Warning, Chart Metric, 내부 ID 제거, 안전한 오류, Backend 값 비재계산, 인증 만료를 검증한다.

### 2026-08-10 실제 브라우저 검증

로컬 MySQL Docker, `local` Profile Backend, Vite와 실제 Google OAuth 세션에서 확인했다.

- `/goal` 인증 보호와 최신 Profile Version 2 로딩
- 목표 1원, 12개월, 월 상환 300,000원: `ALREADY_ACHIEVABLE`, 세 `NOT_REQUIRED` 대안과 월별 Chart 표시
- 목표 30,000,000원, 12개월: `PARTIALLY_ACHIEVABLE`, 지출 절감 불가능 경고와 세 Backend 대안 표시
- 월 상환 0원: `NEGATIVE_AMORTIZATION` Code와 사용자 친화적 설명 표시
- Chart Metric을 순자산에서 투자자산으로 전환
- 새로고침 뒤 목표 입력과 계산 결과가 사라지고 초기 상태로 복귀
- 데스크톱 화면의 Form·상태 Hero·결과 영역을 시각 확인

브라우저 제어 인터페이스는 Viewport 강제 변경과 Console message 수집을 제공하지 않아 실제 모바일 Viewport와 Console 0건을 직접 단정하지 않았다. 모바일은 980px·680px Media Query, 전체 Component 테스트와 Production Build로 회귀 확인했으며 실제 기기 검증은 남은 QA 항목이다. 화면에는 실행 중 사용자에게 보이는 오류나 경고가 없었다. 저장소 내용을 직접 읽는 방식으로도 Goal 구현에 Browser Storage와 Console 기록 코드가 없음을 확인했다.
