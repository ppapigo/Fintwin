# Baseline Financial Simulation API

이 API는 최신 Financial Profile과 사용자가 명시한 가정만으로 월별 금융상태를 계산한다. 미래 수익이나 경제 상황을 예측·추천·보장하지 않으며 LLM이나 외부 AI를 사용하지 않는다. 동일한 Profile, 시작 연월, 기간과 가정에는 항상 동일한 결과를 반환하고 결과는 DB에 저장하지 않는다.

## API

`POST /api/simulations/baseline`

사용자 ID와 Profile ID는 요청에 포함하지 않는다. `CurrentUserIdProvider`가 식별한 사용자의 가장 높은 Financial Profile version을 사용하며 Profile이 없으면 `404 Not Found`를 반환한다.

```json
{
  "startYearMonth": "2026-08",
  "horizonMonths": 60,
  "assumptions": {
    "annualIncomeGrowthRate": 3.0,
    "annualInflationRate": 2.0,
    "annualDepositInterestRate": 2.5,
    "annualInvestmentReturnRate": 5.0,
    "monthlyDebtPayment": 500000.00
  }
}
```

- `horizonMonths`는 12, 36, 60 중 하나다.
- 모든 연 비율은 퍼센트 단위다. `3.0`은 연 3%다.
- 소득증가율·물가상승률·투자수익률은 -100~100%, 예금이율은 0~100%다.
- 월 대출상환액은 0 이상이다. 부채가 있으면 필수이며, 부채가 없으면 계산에 사용되는 값은 0으로 정규화된다.
- `startYearMonth`를 명시하므로 현재 시각에 의존하지 않는다.

## Profile 입력 매핑

- 월초 유동자산 = `cashAssets + deposits`
- 월초 투자자산 = `investmentAssets`
- 월초 부채 = `totalLoanBalance`
- 대출 연이율 = `loanInterestRate`
- 월 소득·고정지출·변동지출·저축 예정액·투자 예정액은 최신 Profile 값을 사용한다.

Profile 원본 행과 DTO는 시뮬레이션 중 변경되지 않는다.

## 월별 계산 순서

1. 월초 유동자산·투자자산·부채를 확정한다.
2. 월 소득을 반영하고 고정·변동지출을 차감한다.
3. 월초 부채에서 대출이자를 계산한다.
4. 실제 상환액을 이자와 원금으로 분리한다.
5. 의무지출 후 가용 현금 안에서 저축 배정액을 기록한다.
6. 저축 배정 후 남은 가용 현금 안에서 투자 납입액을 제한한다.
7. 월초 유동자산에 예금이자, 월초 투자자산에 투자손익을 적용한다.
8. 월말 자산·부채·순자산과 누적값 및 위험 플래그를 기록한다.

Month 1은 Profile의 현재 소득과 지출을 그대로 사용한다. Month 2부터 소득증가율과 물가상승률을 매월 복리 적용한다.

## 계산 공식과 반올림

```text
명목 월이율 = 연 비율 / 100 / 12
월 대출이자 = 월초 부채 * 대출 월이율
실제 대출상환액 = min(설정 상환액, 월초 부채 + 당월 이자)
원금 상환액 = max(실제 상환액 - 당월 이자, 0)
월말 부채 = 월초 부채 + 당월 이자 - 실제 상환액

예금이자 = max(월초 유동자산, 0) * 예금 월이율
투자손익 = 월초 투자자산 * 투자 월수익률
총 금융자산 = 월말 유동자산 + 월말 투자자산
순자산 = 총 금융자산 - 월말 부채
```

모든 금액은 계산 단계마다 소수점 둘째 자리, `RoundingMode.HALF_UP`으로 반올림한다. 비율 계산은 소수점 16자리 정밀도를 유지한 뒤 금액으로 변환할 때만 금액 반올림을 적용한다. `double`과 `float`은 사용하지 않는다.

## 저축·투자·대출 회계 처리

- 저축 배정액은 유동자산 내부에서 저축 목적으로 남겨 둔 금액이다. 유동자산에서 차감 후 재가산하지 않으며 순자산을 이중 증가시키지 않는다.
- 투자 납입액은 유동자산에서 투자자산으로 이동하는 내부 이전이다. 납입 자체는 총 금융자산과 순자산을 바꾸지 않는다.
- 당월 투자 납입액에는 다음 달부터 수익률이 적용된다.
- 대출원금 상환은 현금과 부채를 함께 줄이므로 그 자체로 순자산을 줄이지 않는다. 대출이자는 비용으로 순자산을 감소시킨다.
- 상환액이 이자보다 작으면 미납 이자가 부채에 더해지고 `negativeAmortization=true`다.
- 부채 완납 후 설정 상환액은 더 이상 현금에서 차감하지 않는다.
- 의무지출 후 현금이 부족하면 음수 유동자산을 그대로 반환하고 `cashShortfall=true`로 표시하며 추가 저축·투자를 실행하지 않는다.

## 응답 구조

응답에는 사용한 `financialProfileId`, `financialProfileVersion`, 시작 연월, 기간, 계산에 적용된 전체 가정, 월별 결과, 체크포인트, 최종 누적값과 계산 기준이 포함된다.

```json
{
  "financialProfileId": 42,
  "financialProfileVersion": 3,
  "startYearMonth": "2026-08",
  "horizonMonths": 60,
  "assumptions": {
    "annualIncomeGrowthRate": 3.0,
    "annualInflationRate": 2.0,
    "annualDepositInterestRate": 2.5,
    "annualInvestmentReturnRate": 5.0,
    "monthlyDebtPayment": 500000.00
  },
  "monthlyResults": [
    {
      "monthNumber": 1,
      "yearMonth": "2026-08",
      "income": 3000000.00,
      "cashShortfall": false,
      "negativeAmortization": false
    }
  ],
  "checkpoints": [
    { "monthNumber": 12, "yearMonth": "2027-07" },
    { "monthNumber": 36, "yearMonth": "2029-07" },
    { "monthNumber": 60, "yearMonth": "2031-07" }
  ],
  "finalCumulativeTotals": {},
  "calculationBasis": {
    "monthlyRateFormula": "annual percentage / 100 / 12",
    "moneyRounding": "2 decimals, HALF_UP"
  }
}
```

예시는 구조 설명용이며 특정 금융 결과의 기대값이나 보장이 아니다. 12개월 요청은 12개월, 36개월 요청은 12·36개월, 60개월 요청은 12·36·60개월 체크포인트를 반환한다.
