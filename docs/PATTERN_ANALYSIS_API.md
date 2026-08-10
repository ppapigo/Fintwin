# Financial Pattern Analysis API

## 개요와 개인정보 처리 흐름

`POST /api/patterns/analyze-csv`는 인증된 사용자가 업로드한 FinTwin 표준 CSV를 요청 처리 중 메모리에서만 정규화하고, 결정론적 규칙으로 월별 금융 패턴과 Financial Profile 초안을 계산한다.

```text
multipart stream -> 보안 검증 -> CSV parser -> NormalizedTransaction
                 -> 순수 Java Pattern Engine -> 집계 응답과 Profile 초안
```

- CSV 원문, 정규화 거래 및 분석 결과를 DB·파일·캐시·세션에 저장하지 않는다.
- 외부 AI, 외부 LLM 또는 외부 API를 호출하지 않는다.
- 거래 원문 목록은 응답하지 않는다. 반복거래 확인용 `displayDescription`만 현재 응답에서 제공하며 저장하지 않는다.
- 분석 API는 Financial Profile을 생성하거나 수정하지 않는다. 사용자가 초안을 확인한 뒤 기존 Profile API를 명시적으로 호출해야 한다.
- 현재 구현은 은행별 원본 CSV를 직접 인식하지 않는다. 사용자가 아래 표준 CSV로 변환해야 한다.

## 요청

```http
POST /api/patterns/analyze-csv
Content-Type: multipart/form-data
```

multipart 필드명은 `file`이며 `.csv` 파일만 허용한다. Content-Type 값만 신뢰하지 않고 파일명, 실제 UTF-8 디코딩 및 CSV 구조를 함께 검사한다.

```http
POST /api/patterns/analyze-csv HTTP/1.1
Authorization: <현재 환경의 인증 정보>
X-CSRF-TOKEN: <현재 보안 설정에서 요구하는 경우 발급받은 토큰>
Content-Type: multipart/form-data; boundary=...

--...
Content-Disposition: form-data; name="file"; filename="fintwin-transactions-sample.csv"
Content-Type: text/csv

<CSV bytes>
--...--
```

## CSV 형식

필수 헤더는 `transactionDate,type,amount,category,description`이고 `transactionId`만 선택 헤더다. 헤더명은 정확히 일치해야 한다.

```csv
transactionDate,type,amount,category,description,transactionId
2026-01-25,INCOME,3000000,SALARY,Synthetic Monthly Salary,sample-001
```

`type`:

- `INCOME`: 소득
- `EXPENSE`: 소비
- `SAVING_TRANSFER`: 저축 배정
- `INVESTMENT_TRANSFER`: 투자 배정
- `DEBT_PAYMENT`: 대출 상환
- `TRANSFER`: 계좌 간 이동이며 소득·소비에서 제외

`category`:

`SALARY`, `SIDE_INCOME`, `HOUSING`, `UTILITIES`, `FOOD`, `TRANSPORTATION`, `HEALTHCARE`, `EDUCATION`, `ENTERTAINMENT`, `SHOPPING`, `INSURANCE`, `DEBT`, `SAVINGS`, `INVESTMENT`, `OTHER`, `UNCATEGORIZED`

지원하지 않는 값은 자동 치환하지 않고 400 오류로 거절한다. 모든 금액은 양수 `BigDecimal`이며 방향은 `type`으로 표현한다.

## 보안 제한

- 최대 파일 크기: 2 MiB(2,097,152 bytes)
- 최대 거래 행: 10,000개
- 최대 분석 기간: 최초 월부터 최종 월까지 60개월
- 인코딩: 엄격한 UTF-8, UTF-8 BOM 허용
- 날짜: `yyyy-MM-dd`, 미래 날짜 금지
- `description`: 최대 100자
- `transactionId`: 최대 100자, 값이 있으면 파일 내 중복 금지
- 중복·누락·미지원 헤더와 행별 필드 수 불일치를 거절
- 제어문자와 NUL을 거절
- 직접식별 헤더 `accountNumber`, `residentRegistrationNumber`, `cardNumber`, `phoneNumber`, `email`, `name`을 거절

오류 응답은 오류 코드, 행 번호, 컬럼명, 일반화된 원인만 포함하며 description, transactionId 또는 입력 금액 원문을 포함하지 않는다.

```json
{
  "status": 400,
  "code": "CSV_INVALID_AMOUNT",
  "message": "CSV validation failed",
  "path": "/api/patterns/analyze-csv",
  "fieldErrors": [
    {"field": "amount", "message": "Transaction amount must be a decimal number", "rowNumber": 7}
  ]
}
```

## 월별 계산 규칙

최초 거래 월부터 최종 거래 월까지 거래가 없는 중간 월도 0원 월로 포함한다. `TRANSFER`는 소득·소비에 포함하지 않는다.

```text
monthlySurplus = income - expenses - debtPayments
liquidityAfterAllocations = monthlySurplus - savingTransfers - investmentTransfers
savingsRatePercent = average(max(monthlySurplus, 0)) / average(income) * 100
```

평균 월 소득이 0이면 `savingsRatePercent`는 `null`이고 `NO_INCOME_FOUND` 경고를 반환한다. 금액은 소수 둘째 자리, 비율은 소수 넷째 자리에서 `HALF_UP`으로 반올림한다.

카테고리별로 전체 소비, 월평균 소비, 전체 소비 대비 비율을 제공한다. 소득·소비·현금흐름 변동성은 평균절대편차(MAD)로 계산한다.

```text
MAD = sum(abs(monthValue - monthlyAverage)) / includedMonthCount
volatilityRatioPercent = MAD / abs(monthlyAverage) * 100
```

소비 추세는 기간을 앞 절반과 뒤 절반으로 나누고 `(뒤 평균 - 앞 평균) / 앞 평균 * 100`으로 계산한다. 5% 초과는 `INCREASING`, -5% 이상 5% 이하는 `STABLE`, -5% 미만은 `DECREASING`이다. 3개월 미만이거나 앞 평균이 0이면 `INSUFFICIENT_DATA`다.

## 반복거래와 고정·변동지출 추정

description의 앞뒤 공백 제거, 연속 공백 축약, 대소문자 통일 후 type·category가 같은 거래를 묶는다. 서로 다른 최소 3개월에 존재하고 각 월의 대표 금액이 중앙값의 ±10% 안에 있어야 반복거래로 판정한다.

한 달에 여러 번 발생하면 해당 월의 합계와 발생 횟수를 따로 유지하고 `월 합계 / 월 발생 횟수`를 허용오차 판단용 대표 금액으로 쓴다. 응답에는 월평균 합계와 월평균 발생 횟수를 각각 제공한다.

```text
estimatedFixedExpenses = min(평균 월 소비, 반복 EXPENSE 그룹의 월평균 합계)
estimatedVariableExpenses = max(평균 월 소비 - estimatedFixedExpenses, 0)
```

이는 확정 분류가 아니라 사용자 검토가 필요한 추정이다. 3개월 미만은 `LOW`, 3~5개월은 `MEDIUM`, 6개월 이상은 `HIGH` 데이터 충분도를 표시한다.

## Financial Profile 초안과 비교

CSV에서 추정하는 필드는 `monthlyIncome`, `monthlyFixedExpenses`, `monthlyVariableExpenses`, `monthlySavings`, `monthlyInvestment`뿐이다. 자산, 부채, 금리 및 금융 목표는 추론하지 않는다.

기존 최신 Profile이 있으면 자산·부채·금리 값을 보존 필드로 보여 주고 다음 방향으로 차이를 계산한다.

```text
delta = draft - currentProfile
```

Profile이 없으면 `currentProfileComparison`은 `null`이고 추론하지 못한 항목은 `notInferredFields`에 표시한다. 어느 경우에도 새 스냅샷을 만들지 않는다.

## 응답 구조

응답에는 `algorithmVersion`, 분석 기간, 거래 수, 월별 cash flow, 평균 패턴, 카테고리 소비, 반복거래, 고정·변동지출 추정, 변동성, 소비 추세, 적자 월, Profile 초안, 최신 Profile 비교, `analysisRules`, 경고 및 `privacyNotice`가 포함된다. 원본 거래 목록은 포함하지 않는다.

```json
{
  "algorithmVersion": "fintwin-pattern-v1",
  "analysisPeriod": {"startYearMonth": "2026-01", "endYearMonth": "2026-06", "includedMonthCount": 6},
  "transactionCount": 37,
  "averages": {
    "monthlyIncome": 3000000.00,
    "monthlyExpenses": 1355000.00,
    "monthlySavingTransfers": 500000.00,
    "monthlyInvestmentTransfers": 300000.00,
    "monthlySurplus": 1645000.00,
    "monthlyLiquidityAfterAllocations": 845000.00,
    "savingsRatePercent": 57.3333,
    "deficitMonthCount": 1
  },
  "expenseClassification": {
    "estimatedFixedExpenses": 700000.00,
    "estimatedVariableExpenses": 655000.00,
    "dataConfidence": "HIGH"
  },
  "spendingTrend": {"classification": "INCREASING"},
  "deficitMonths": ["2026-06"],
  "currentProfileComparison": null,
  "privacyNotice": {
    "storage": "The original CSV, normalized transactions, and analysis result are not stored in a database or file system.",
    "externalTransfer": "Transaction data is not sent to an external AI or external API."
  }
}
```

위 숫자는 [샘플 CSV](examples/fintwin-transactions-sample.csv)의 주요 집계값이다. 생략된 응답 필드는 실제 API에서 함께 반환된다.

## 경고 코드

- `INSUFFICIENT_HISTORY`
- `NO_INCOME_FOUND`
- `NO_EXPENSE_FOUND`
- `HIGH_INCOME_VOLATILITY`
- `HIGH_EXPENSE_VOLATILITY`
- `NEGATIVE_CASH_FLOW_MONTHS`
- `LOW_SAVINGS_RATE`
- `RECURRING_PATTERN_UNAVAILABLE`
- `MANY_UNCATEGORIZED_TRANSACTIONS`
- `PROFILE_REVIEW_REQUIRED`

정량 임계값은 `FinancialPatternRules` 한 곳에서 관리하고 응답의 `analysisRules`에 공개한다. 경고는 금융 보장이나 조언이 아니라 사용자가 결과를 검토해야 한다는 규칙 기반 신호다.
