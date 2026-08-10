# Scenario Comparison API

FinTwin의 시나리오 비교는 외부 AI를 호출하지 않는 결정론적 계산이다. 인증 사용자의 최신 `FinancialProfile` 스냅샷과 요청의 가정·구조화 이벤트만 사용하며, Profile과 결과를 변경하거나 저장하지 않는다.

## API

`POST /api/simulations/compare`

사용자 ID와 Profile ID는 요청 본문에서 받지 않는다. 현재 단계에서는 격리된 `CurrentUserIdProvider`가 제공하는 사용자 ID로 최신 Profile을 조회하며, 향후 실제 인증 principal 기반 구현으로 교체한다.

```json
{
  "scenarioName": "자동차 현금 구매와 생활비 절감",
  "startYearMonth": "2026-08",
  "horizonMonths": 36,
  "assumptions": {
    "annualIncomeGrowthRate": 3.0,
    "annualInflationRate": 2.0,
    "annualDepositInterestRate": 2.5,
    "annualInvestmentReturnRate": 5.0,
    "monthlyDebtPayment": 500000
  },
  "events": [
    {
      "eventId": "car-purchase",
      "eventType": "ONE_TIME_EXPENSE",
      "effectiveYearMonth": "2027-08",
      "amount": 30000000,
      "description": "자동차 현금 구매"
    },
    {
      "eventId": "living-cost-cut",
      "eventType": "RECURRING_EXPENSE_CHANGE",
      "startYearMonth": "2027-08",
      "endYearMonth": "2028-07",
      "monthlyDelta": -200000,
      "description": "생활비 절감"
    }
  ]
}
```

응답은 Profile ID·버전, 정규화된 이벤트, 가정, baseline 전체 결과, what-if 전체 결과, 12·36·60개월 중 기간 내 체크포인트 비교, 최종 비교, 영향 요약과 경고를 포함한다. `baseline`과 `whatIf`는 같은 `MonthlyFinancialSimulationEngine` 결과 형식을 사용한다.

```json
{
  "financialProfileId": 12,
  "financialProfileVersion": 3,
  "scenarioName": "자동차 현금 구매와 생활비 절감",
  "startYearMonth": "2026-08",
  "horizonMonths": 36,
  "normalizedEvents": [],
  "baseline": { "monthlyResults": [], "checkpoints": [] },
  "whatIf": { "monthlyResults": [], "checkpoints": [] },
  "checkpointComparisons": [],
  "finalComparison": {
    "netWorthDelta": -27600000.00
  },
  "impactSummary": {
    "incomeDelta": 0.00,
    "consumptionDelta": 27600000.00,
    "netWorthDelta": -27600000.00,
    "residualDelta": 0.00
  },
  "warnings": []
}
```

위 숫자는 응답 구조 설명용 예시이며 실제 결과를 보장하지 않는다.

## 지원 이벤트와 필수 필드

| eventType | 시점 필드 | 값 필드 | 규칙 |
|---|---|---|---|
| `ONE_TIME_EXPENSE` | `effectiveYearMonth` | 양수 `amount` | 해당 월 소비와 유동자산에 한 번 반영 |
| `RECURRING_EXPENSE_CHANGE` | `startYearMonth`, `endYearMonth` | signed `monthlyDelta` | 양수는 지출 증가, 음수는 감소, 최종 지출은 0 이상 |
| `INCOME_CHANGE` | `startYearMonth`, `endYearMonth` | signed `monthlyDelta` | 최종 소득은 0 이상 |
| `INCOME_PAUSE` | `startYearMonth`, `endYearMonth` | 없음 | 적용 월 최종 소득을 0으로 만듦 |
| `INVESTMENT_CONTRIBUTION_CHANGE` | `startYearMonth`, `endYearMonth` | signed `monthlyDelta` | 계획 투자액은 0 이상이며 실제 투자는 가용 현금 이하 |
| `EXTRA_DEBT_REPAYMENT` | `effectiveYearMonth` | 양수 `amount` | 요청액·남은 부채·가용 현금 중 최솟값만 상환 |

모든 이벤트에는 최대 100자의 고유 `eventId`와 최대 200자의 `description`이 필요하다. 요청당 이벤트는 1~20개다. `description`은 계산에 사용하지 않는다. 금액과 비율은 모두 `BigDecimal`이다.

지원하지 않는 신규 대출, 자동차 할부, 복수 대출, 변동금리, 부동산·개별 주식 가격, 세금, 보험료 조정, 환율, 금융상품 추천 이벤트는 `400 INVALID_REQUEST`로 거절한다.

## 기간과 정규화

- `endYearMonth`는 포함 범위다. `2027-01`~`2027-06`은 6개월에 적용한다.
- 기간 이벤트가 시뮬레이션 범위와 일부만 겹치면 겹치는 구간으로 잘라 적용하고 경고를 반환한다.
- 기간 이벤트가 완전히 범위 밖이면 400이다. 일회성 이벤트가 범위 밖이어도 400이다.
- 이벤트는 `eventId`로 정렬해 응답하며, 월별로 같은 종류의 값을 합산한다. 요청 순서는 계산 결과에 영향을 주지 않는다.

## 월별 적용 순서

1. baseline 소득 증가율
2. baseline 지출 물가상승률
3. `INCOME_CHANGE` 합산
4. `INCOME_PAUSE` 우선 적용
5. `RECURRING_EXPENSE_CHANGE` 합산
6. `INVESTMENT_CONTRIBUTION_CHANGE` 합산
7. `ONE_TIME_EXPENSE` 합산
8. 기본 대출 상환
9. `EXTRA_DEBT_REPAYMENT`
10. 예금이자·투자수익과 월말 자산·부채 계산

## 비교와 영향 요약

모든 delta의 방향은 다음과 같다.

```text
delta = what-if - baseline
```

체크포인트와 최종 비교에는 유동자산, 투자자산, 총 금융자산, 부채, 순자산과 누적 소득·소비·대출이자·원금상환·투자액·투자수익 delta가 포함된다. 영향 요약에는 `incomeDelta`, `consumptionDelta`, `debtInterestDelta`, `principalRepaidDelta`, `investmentContributionDelta`, `investmentReturnDelta`, `liquidAssetsDelta`, `debtDelta`, `netWorthDelta`를 제공한다.

원금 상환과 투자 납입은 자산 내부 이동 성격이 있으므로 순자산 기여 항목을 임의로 중복 합산하지 않는다. 단순 기여 항목과 순자산 delta 사이에 예금이자, 복리, 현금 제약, 월별 반올림 등으로 차이가 있으면 `residualDelta`로 명시한다.

이 API는 자연어 이벤트 처리 API가 아니다. LLM이나 외부 AI를 호출하지 않으며 금융 원문·거래내역·계좌번호·금융기관명을 외부로 전송하지 않는다. 결과는 입력 가정에 기반한 결정론적 시뮬레이션이며 예측이나 보장이 아니다.
