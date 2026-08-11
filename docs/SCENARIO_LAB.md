# FinTwin Scenario Lab

`/scenario-lab`은 인증된 사용자의 최신 Financial Profile Snapshot과 하나의 공통 Assumption을 사용해 수정 불가능한 Baseline A와 최대 네 개의 구조화 Scenario를 비교한다. 계산은 기존 `MonthlyFinancialSimulationEngine`이 수행하며 외부 AI, 결과 저장, 자동 재시도는 사용하지 않는다.

## 사용자 흐름

```text
로그인
→ 최신 Profile 확인
→ 공통 시작 월·12/36/60개월·Assumption 입력
→ Scenario B~E 추가·복제·삭제
→ Scenario별 6종 FinancialEvent 구성
→ POST /api/simulations/compare-multiple
→ 비교 카드·표·월별 Chart·Checkpoint·Risk 확인
```

Profile이 없으면 계산하지 않고 `/profile/setup` 이동을 안내한다. 입력과 결과는 React 메모리에만 존재하므로 새로고침하면 초기화된다.

## API 계약

`POST /api/simulations/compare-multiple`

```json
{
  "startYearMonth": "2026-08",
  "horizonMonths": 60,
  "assumptions": {
    "annualIncomeGrowthRate": "0",
    "annualInflationRate": "0",
    "annualDepositInterestRate": "0",
    "annualInvestmentReturnRate": "0",
    "monthlyDebtPayment": "300000"
  },
  "scenarios": [
    {
      "scenarioKey": "B",
      "label": "자동차 구매",
      "events": [
        {
          "eventId": "event-example",
          "eventType": "ONE_TIME_EXPENSE",
          "effectiveYearMonth": "2027-01",
          "amount": "30000000",
          "description": "합성 자동차 구매 조건"
        }
      ]
    }
  ]
}
```

- Scenario는 1~4개, Scenario별 Event는 0~20개, 요청 전체는 최대 80개다.
- `scenarioKey`는 요청 안에서 고유해야 하며 영문·숫자·`_`·`-`만 허용한다.
- label은 1~100자이고 제어문자를 허용하지 않는다.
- Event ID는 각 Scenario 안에서 고유해야 한다.
- Event 유형·필수 필드·금액·기간 검증은 단일 Compare의 `FinancialEventMapper`를 재사용한다.
- 알 수 없는 요청 필드는 거부하므로 User ID, Profile ID와 Tool 이름을 받을 수 없다.
- 금액과 비율은 Backend `BigDecimal`, Frontend 문자열로 유지한다.

응답은 `financialProfileVersion`, 기간, 가정, Baseline 전체 결과, Scenario별 전체 결과, Backend Checkpoint 비교, 비교 요약, 구조화 경고, 계산 기준과 면책을 반환한다. 내부 Financial Profile ID는 Multi 응답에 포함하지 않는다.

## Engine 실행 횟수

서비스가 인증 Principal의 내부 User ID로 최신 Profile을 정확히 한 번 조회하고 공통 입력을 만든다.

| 4개 Scenario 요청 | 횟수 |
|---|---:|
| 최신 Profile 조회 | 1 |
| Baseline 엔진 실행 | 1 |
| Scenario 엔진 실행 | 4 |
| 외부 AI 호출 | 0 |
| 자동 재시도 | 0 |

Controller, Response Mapper와 Risk Checker는 엔진을 호출하지 않는다. 단일 Compare와 Multi Compare는 같은 `FinancialEventMapper`, `ScenarioMonthlyAdjustmentProvider`, 엔진과 `ScenarioComparisonResponse.ComparisonResult` Delta 함수를 사용한다.

## 비교 지표와 Delta

모든 Delta 방향은 다음과 같다.

```text
scenario - baseline
```

Scenario 응답은 월별 결과, 12·36·60개월 Checkpoint, 최종 유동·투자·총 금융자산, 부채, 순자산, 마지막 달 가처분 현금흐름, 누적 소득·소비·저축·투자·대출이자·원금상환, Baseline Delta와 `residualDelta`를 제공한다. Scenario 간 직접 Delta나 순위는 계산하지 않는다.

Frontend 비교 표·Tooltip은 Backend 금액 문자열을 표시한다. Chart 좌표에서만 제한적으로 `Number`로 변환하며 누락된 월은 보간하거나 연결하지 않는다. Checkpoint는 월별 Series에서 다시 만들지 않고 `checkpointComparisons`만 표시한다.

## Risk Checker

`ScenarioResultRiskChecker`는 이미 계산된 월별 결과, 최종 결과와 적용 Event만 검사한다. 새 임계값이나 재시뮬레이션을 만들지 않는다.

- `CASH_SHORTFALL`
- `NEGATIVE_AMORTIZATION`
- `NET_WORTH_BELOW_BASELINE`
- `LIQUID_ASSETS_BELOW_BASELINE`
- `DEBT_ABOVE_BASELINE`
- `INVESTMENT_CONTRIBUTION_CASH_LIMITED`
- `EXTRA_DEBT_REPAYMENT_LIMITED`
- `EVENT_PERIOD_CLIPPED`

Frontend는 Code를 고정된 안전 문구로 매핑하고 Backend 원문 오류·Stack Trace는 표시하지 않는다. 알 수 없는 Code도 숨기지 않고 일반 안전 안내를 표시한다.

## 개인정보와 계산 경계

- 인증은 `CurrentUserIdProvider`만 사용하며 요청·URL·화면에 User ID, Profile ID, OAuth Subject, Session ID를 넣지 않는다.
- Scenario, Event와 결과를 DB, 파일, 캐시, 세션, Local Storage, Session Storage, IndexedDB에 저장하지 않는다.
- 금융값을 Console이나 Analytics에 기록하지 않는다.
- OpenAI Provider와 자연어 Agent를 호출하지 않는다.
- 자연어 What-if에서 향후 검증된 Event를 메모리로 가져올 수 있지만 Scenario Lab 실행 자체는 새 AI 호출을 만들지 않는다.
- Frontend는 금융값, Delta, Risk, Checkpoint, 누적값과 순위를 계산하거나 보정하지 않는다.

## 검증과 한계

자동 테스트는 Profile 1회 조회, Baseline 1회, Scenario별 1회, 단일/Multi 골든 결과 일치, 이벤트 순서 결정론, Scenario·Event 경계, 사용자 분리, 결과 미저장, 내부 ID 비노출, 60개월·4개 응답 2 MiB 미만을 검증한다. Frontend 테스트는 Profile 없음, 편집·복제·삭제·4개 제한, 요청 DTO, 문자열 금액, 중복 제출, 카드·표·Chart·Checkpoint·Warning, Storage 미사용과 인증 만료를 검증한다.

### 2026-08-11 실제 브라우저 검증

로컬 MySQL 8.4 Docker, `local` Profile Backend, Vite와 실제 Kakao OAuth2 세션에서 다음을 확인했다.

- Profile v2, 기존 2개 불변 Snapshot을 기준으로 Baseline + 3개 Scenario를 60개월 실행
- B: 일회성 지출 1,000,000원, C: B 복제 후 월 소비 100,000원 절감 6개월로 변경, D: 소득 3개월 중단
- 최종 순자산 Delta: B `-1,000,000원`, C `+600,000원`, D `-9,000,000원`
- 4개 결과 카드, 비교 표, 1·3·5년 Backend Checkpoint와 구조화 Risk 표시
- Chart Metric을 순자산에서 투자자산으로 전환
- 1440px Viewport에서 문서 `scrollWidth 1425 = clientWidth 1425`로 가로 넘침 없음
- 390px Viewport에서 문서 `scrollWidth 375 = clientWidth 375`, 모바일 Scenario C 선택, Desktop 선택기 숨김
- 비교 표는 모바일에서 페이지가 아니라 표 Wrapper 내부에서만 가로 스크롤
- 새로고침 뒤 결과 0건, Scenario B 1개와 기본 Assumption으로 초기화
- 실행 전후 Profile v2와 2개 Snapshot 유지
- Browser Console warning/error 0건, Backend ERROR 0건
- Backend 로그의 OpenAI/Responses API 호출 흔적 0건이며 Scenario Lab 실행 경로에 AI Adapter 의존성 없음

현재는 세금·수수료·실제 가격 변동·상품 적합성·미래 소득 실현 가능성을 판단하지 않는다. 결과는 사용자 가정에 따른 결정론적 비교이며 미래 결과, 목표 달성 또는 투자수익을 보장하지 않고 금융상품 추천이나 투자 자문이 아니다.
