# Goal Reverse Simulation API

FinTwin Goal Reverse Simulation은 인증 사용자의 최신 Financial Profile과 사용자가 제공한 가정을 바탕으로 목표 시점의 순자산을 달성하는 데 필요한 월별 현금흐름 변화의 최소 경계를 탐색한다. 외부 AI나 닫힌 형태의 추정 공식을 사용하지 않고 기존 `MonthlyFinancialSimulationEngine`을 반복 실행한다.

결과는 설정한 가정 아래의 결정론적 시뮬레이션이며 금융 조언, 수익 예측 또는 목표 달성 보장이 아니다.

## API

`POST /api/goals/reverse-simulate`

사용자 ID와 Profile ID는 요청 본문에서 받지 않는다. `CurrentUserIdProvider`가 식별한 사용자의 최신 불변 Profile 스냅샷을 사용하며 Goal과 계산 결과를 DB에 저장하지 않는다.

```json
{
  "goalType": "TARGET_NET_WORTH",
  "targetAmount": 50000000,
  "startYearMonth": "2026-08",
  "horizonMonths": 36,
  "assumptions": {
    "annualIncomeGrowthRate": 3.0,
    "annualInflationRate": 2.0,
    "annualDepositInterestRate": 2.5,
    "annualInvestmentReturnRate": 5.0,
    "monthlyDebtPayment": 500000
  }
}
```

- `goalType`, `targetAmount`, `startYearMonth`, `horizonMonths`, `assumptions`는 필수다.
- `targetAmount`는 0보다 커야 한다.
- `horizonMonths`는 12, 36, 60만 허용한다.
- Assumption 범위와 부채가 있을 때의 `monthlyDebtPayment` 필수 규칙은 baseline API와 동일하다.
- 목표 종료 월은 `startYearMonth + horizonMonths - 1개월`이다.
- 지원하지 않는 Goal 유형은 `400 INVALID_REQUEST`, Profile이 없으면 404를 반환한다.

## 지원 Goal

이번 단계는 `TARGET_NET_WORTH`만 지원한다.

```text
목표 달성 = 목표 종료 월의 순자산 >= targetAmount
```

목표 현금, 목표 투자자산, 부채 전액 상환, 주택·은퇴 목표, 금융상품 수익률, 개별 자산가격 목표는 지원하지 않는다.

## Goal 상태

| 상태 | 의미 |
|---|---|
| `ALREADY_ACHIEVABLE` | baseline 최종 순자산이 이미 목표 이상이며 추가 탐색을 실행하지 않음 |
| `ACHIEVABLE` | 지출 절감을 포함한 하나 이상의 지원 대안이 목표를 달성함 |
| `PARTIALLY_ACHIEVABLE` | 지출 절감은 불가능하지만 소득 증가 또는 절감 후 투자 대안은 가능함 |
| `NOT_ACHIEVABLE` | 검증된 탐색 상한 안에서 어떤 지원 대안도 목표를 달성하지 못함 |

잘못된 요청은 Goal 상태로 반환하지 않고 공통 400 오류 형식으로 처리한다.

대안 상태는 `NOT_REQUIRED`, `ACHIEVABLE`, `INFEASIBLE`이다. 대안은 항상 다음 의미 순서로 반환하며 금액만으로 어느 대안이 최적이라고 단정하지 않는다.

1. `REDUCE_EXPENSE`
2. `INCREASE_INCOME`
3. `REDUCE_EXPENSE_AND_INVEST`

## 지원 대안

### REDUCE_EXPENSE

시작 월부터 목표 종료 월까지 `RECURRING_EXPENSE_CHANGE`를 적용한다.

```text
monthlyDelta = -requiredMonthlyAmount
```

현재 엔진은 음수 지출 조정을 변동지출부터 줄인다. 고정지출까지 자동으로 절감하는 계획을 만들지 않기 위해 최대 절감액은 baseline 기간 중 월 변동지출의 최솟값을 1원 단위로 내림한 값이다. 이 상한으로 목표를 달성하지 못하면 대안은 `INFEASIBLE`이다.

### INCREASE_INCOME

전체 기간에 양수 `INCOME_CHANGE`를 적용한다.

```text
monthlyDelta = requiredMonthlyAmount
```

이는 세금이나 실제 소득 발생 가능성을 판단하지 않는 가정 기반 경계값이다. 필요한 금액이 현재 월 소득보다 크면 경고한다.

### REDUCE_EXPENSE_AND_INVEST

동일 금액 X의 두 이벤트를 전체 기간에 적용한다.

```text
RECURRING_EXPENSE_CHANGE.monthlyDelta = -X
INVESTMENT_CONTRIBUTION_CHANGE.monthlyDelta = +X
```

투자 예정액은 절감액을 초과하지 않는다. 실제 투자액은 기존 엔진이 저축 배정 후 가용 현금 안에서 제한하며 당월 납입에는 다음 달부터 투자손익이 적용된다. 투자수익률은 사용자 Assumption이고 양수 수익을 보장하지 않는다.

## 저축 배정과 실제 현금흐름

Profile의 `monthlySavings`는 유동자산 내부의 목적 배정이다. 이 값을 증가시키는 것만으로 자산이 새로 생기지 않는다. Goal Solver는 `SimulationInput`이나 Profile의 `plannedMonthlySavings`를 변경하지 않으며 다음 실제 변화만 이벤트로 만든다.

- 지출 감소
- 소득 증가
- 지출 감소로 생긴 현금의 투자 이전

## 목표 부족 금액과 마진

```text
goalGap = max(targetAmount - baselineFinalNetWorth, 0)
goalMargin = planFinalNetWorth - targetAmount
```

`goalMargin`이 양수면 목표 초과, 0이면 정확히 달성, 음수면 부족이다.

## 탐색 알고리즘

각 후보의 성공 여부는 반드시 기존 월별 엔진의 목표 종료 월 순자산으로 판단한다.

```text
candidateWorks = simulatedFinalNetWorth >= targetAmount
```

1. baseline을 한 번 계산한다.
2. baseline이 이미 목표 이상이면 세 대안을 `NOT_REQUIRED`, 필요 금액 0, 반복 횟수 0으로 반환한다.
3. 각 대안은 1원에서 시작해 후보를 두 배로 늘리며 성공 상한을 찾는다.
4. 상한을 찾으면 실패 하한과 성공 상한 사이를 `BigDecimal` 이진 탐색한다.
5. 성공 최소 후보를 다시 실행하고 1원 작은 후보가 실패하는지 다시 확인한다.

탐색 해상도는 1원이다. 후보 금액은 모두 `BigDecimal`이며 `double`과 `float`을 사용하지 않는다. 대안당 최대 엔진 실행 횟수는 128회다. 소득 증가의 안전 상한은 월 `99,999,999,999,999,999원`이며 이 값에서도 실패하면 `INFEASIBLE`과 `SEARCH_LIMIT_REACHED`를 반환한다. 지출 기반 대안은 이 상한과 변동지출 상한 중 작은 값을 사용한다.

## 단조성 전제와 한계

현재 엔진과 지원 Assumption 범위에서는 다음을 전제로 한다.

- 더 큰 소득 증가는 존재하지 않던 현금을 추가하므로 최종 순자산을 감소시키지 않는다.
- 더 큰 지출 절감은 실제 소비를 줄이며 변동지출 상한 안에서는 최종 순자산을 감소시키지 않는다.
- 절감 후 투자는 절감으로 새로 확보된 금액 이하만 이전한다. 지원되는 최저 투자수익률은 연 -100%로 월별 자산 배수가 양수이므로 추가 납입분의 월말 가치가 음수가 되지 않는다.
- 현금 제약과 월별 반올림은 같은 결과가 이어지는 plateau를 만들 수 있지만 후보 증가에 따른 순자산 역전을 만들지 않는다.

이 전제는 신규 대출, 수수료, 세금, 레버리지, 투자 잔액을 음수로 만드는 규칙 등이 추가되면 다시 검증해야 한다. 단조성이 보장되지 않는 미래 대안에는 현재 이진 탐색을 그대로 적용하면 안 된다.

## 구조화 경고

- `ALREADY_ACHIEVABLE`
- `EXPENSE_REDUCTION_INFEASIBLE`
- `NEGATIVE_INVESTMENT_RETURN`
- `INVESTMENT_RETURN_BELOW_DEPOSIT_RATE`
- `CASH_SHORTFALL`
- `NEGATIVE_AMORTIZATION`
- `INCOME_INCREASE_EXCEEDS_CURRENT_INCOME`
- `SEARCH_LIMIT_REACHED`
- `INVESTMENT_CONTRIBUTION_CASH_LIMITED`

경고는 규칙 기반 코드와 문자열이며 LLM을 사용하지 않는다.

## 응답 구조

응답에는 Profile ID·버전, 목표·기간·가정, 상태, 현재 순자산, baseline 최종 순자산, 목표 부족 금액, baseline 최초 달성 월, baseline 전체 결과, 세 대안, Solver 메타데이터와 경고를 포함한다.

```json
{
  "financialProfileId": 42,
  "financialProfileVersion": 3,
  "goalType": "TARGET_NET_WORTH",
  "targetAmount": 50000000.00,
  "startYearMonth": "2026-08",
  "targetEndYearMonth": "2029-07",
  "horizonMonths": 36,
  "goalStatus": "ACHIEVABLE",
  "currentNetWorth": 12000000.00,
  "baselineFinalNetWorth": 32000000.00,
  "goalGap": 18000000.00,
  "plans": [
    {
      "planType": "REDUCE_EXPENSE",
      "planStatus": "ACHIEVABLE",
      "requiredMonthlyAmount": 500000.00,
      "maximumMonthlyAmountTested": 524288.00,
      "generatedEvents": [],
      "projectedFinalNetWorth": 50000003.00,
      "goalMargin": 3.00,
      "achieved": true,
      "solverIterations": 41,
      "appliedConstraints": [],
      "warnings": [],
      "projectedResult": { "monthlyResults": [] }
    }
  ],
  "solverMetadata": {
    "searchResolution": 1,
    "maximumIterationsPerPlan": 128
  },
  "warnings": [],
  "disclaimer": "This is a deterministic simulation under user-provided assumptions, not financial advice, a return forecast, or a guarantee that the goal will be achieved."
}
```

숫자는 응답 방향과 구조 설명용이며 특정 결과를 보장하지 않는다. `INFEASIBLE` 대안은 `requiredMonthlyAmount=null`이고, `maximumMonthlyAmountTested`, 해당 상한에서의 이벤트와 월별 결과를 제공한다.
