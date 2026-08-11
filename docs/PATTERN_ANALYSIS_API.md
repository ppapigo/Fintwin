# Financial Pattern Analysis API

## 개요와 개인정보 처리 흐름

`POST /api/patterns/analyze-csv`와 `POST /api/patterns/analyze-xlsx`는 인증된 사용자가 업로드한 FinTwin 표준 거래내역을 요청 처리 중 메모리에서만 정규화하고, 결정론적 규칙으로 월별 금융 패턴과 Financial Profile 초안을 계산한다.

```text
multipart stream -> 형식별 보안 검증 -> CSV/XLSX parser
                 -> 공통 TransactionRecordNormalizer -> NormalizedTransaction
                 -> 순수 Java Pattern Engine -> 집계 응답과 Profile 초안
```

- CSV 원문, 정규화 거래 및 분석 결과를 DB·파일·캐시·세션에 저장하지 않는다.
- 외부 AI, 외부 LLM 또는 외부 API를 호출하지 않는다.
- 거래 원문 목록은 응답하지 않는다. 반복거래 확인용 `displayDescription`만 현재 응답에서 제공하며 저장하지 않는다.
- 분석 API는 Financial Profile을 생성하거나 수정하지 않는다. 사용자가 초안을 확인한 뒤 기존 Profile API를 명시적으로 호출해야 한다.
- 현재 구현은 은행별 원본 CSV/XLSX를 직접 인식하지 않는다. 사용자가 아래 FinTwin 표준 형식으로 변환해야 한다.
- 형식별 위험과 오류 코드가 크게 달라 `TransactionFileParser` 공통 인터페이스는 아직 도입하지 않았다. 두 Parser가 공통 `TransactionRecordNormalizer`를 사용하고 이후 Engine과 Profile Draft 흐름을 완전히 공유한다.

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

## XLSX 요청과 형식

```http
POST /api/patterns/analyze-xlsx
Content-Type: multipart/form-data
X-CSRF-TOKEN: <현재 세션의 CSRF token>

file=<FinTwin 표준 .xlsx>
```

- 확장자는 정확히 `.xlsx`여야 하며 `.xls`, `.xlsm`, 확장자를 위장한 CSV/ZIP은 거절한다.
- Workbook에는 보이는 `transactions` 시트 하나만 있어야 한다.
- Header와 거래 유형·Category·날짜·금액·중복 ID 검증은 CSV와 동일하다.
- 날짜와 식별용 셀은 텍스트여야 한다. `amount`만 텍스트 또는 OOXML에 저장된 원시 십진 숫자를 허용하며 `double`로 변환하지 않는다.
- 제한된 빈 행은 허용하지만 값이 일부만 있는 행의 오류를 건너뛰지 않는다.

빈 표준 양식은 인증 후 `GET /api/patterns/xlsx-template`에서 받을 수 있다. 6개월 합성 데이터 문서 예제는 [fintwin-transactions-sample.xlsx](examples/fintwin-transactions-sample.xlsx)다.

### XLSX 보안 검증

- 업로드 선언 크기와 실제 읽은 크기를 각각 2 MiB로 제한한다.
- `FileMagic`과 OOXML ZIP의 필수 Part를 확인한다.
- ZIP Entry 수, Entry별/전체 압축 해제 크기, 압축 비율, 경로 이탈을 제한해 ZIP Bomb을 차단한다.
- VBA/매크로 Part, 암호화 Workbook, 수식 셀, Hyperlink와 외부 Relationship을 거절한다.
- Drawing, Comment, Media, ActiveX, Embedded OLE, Custom XML을 거절한다.
- 숨긴 시트·행·열, 병합 셀, 과도한 Sheet·Row·Cell·빈 행·문자열을 거절한다.
- Parser 예외 원문, 파일명, 셀 값, 거래 설명은 API 오류 응답이나 로그로 전달하지 않는다.

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
    "storage": "The original uploaded file, normalized transactions, and analysis result are not stored in a database or file system.",
    "externalTransfer": "Transaction data is not sent to an external AI or external API."
  }
}
```

위 숫자는 [샘플 CSV](examples/fintwin-transactions-sample.csv)의 주요 집계값이다. 동일한 거래의 CSV/XLSX는 `NormalizedTransaction`, 월별 집계, 평균, 반복 지출, Category 비율, Profile Draft, Warning과 현재 Profile 차이가 모두 같아야 한다. 생략된 응답 필드는 실제 API에서 함께 반환된다.

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

## React 사용자 흐름과 승인 경계

인증 사용자는 `/patterns/import`에서 CSV/XLSX를 선택하거나 Drag & Drop한다. Frontend는 확장자와 2 MiB 제한을 먼저 확인하고 기존 공통 API Client의 HttpSession·CSRF 흐름으로 한 번만 전송한다.

```text
파일 선택 -> 메모리 분석 -> 월별/Category/반복/Warning 확인
         -> 현재 Profile과 Draft 비교 -> 반영 필드 선택
         -> FinancialProfileForm 최종 검토 -> PUT /api/financial-profiles/current
         -> 새 불변 Snapshot 생성
```

- 분석 응답의 `financialProfileId`는 API 정규화 단계에서 제거하고 Version만 표시한다.
- 금액은 응답 JSON을 정밀도 안전하게 읽어 문자열로 유지하며 Frontend에서 Pattern 계산이나 Draft 값을 재계산하지 않는다.
- 분석만으로 Profile을 만들거나 수정하지 않는다. 사용자가 선택한 필드만 현재 Profile에 합성하고 전체 수정 폼을 통과한 뒤 기존 PUT API를 호출한다.
- 파일과 분석 결과는 `localStorage`, `sessionStorage`, URL, Router state에 넣지 않는다.
- 원본 파일명과 거래 설명을 Console에 출력하지 않는다.
- 응답에 포함된 반복 거래는 화면에서 거래 설명 대신 Type과 Category 중심으로 표시한다.

## 한계와 면책

- 은행별 XLSX 자동 인식, `.xls`/`.xlsm`, 여러 시트, 수식 기반 Workbook은 지원하지 않는다.
- Pattern은 업로드 기간의 규칙 기반 관측 결과이며 미래 소득·소비나 금융 성과를 보장하지 않는다.
- Category와 거래 유형이 잘못 분류되면 Draft도 달라질 수 있으므로 저장 전 사용자 확인이 필요하다.
- 분석은 생성형 AI가 아니라 결정론적 Java Engine이 수행하며 금융상품 추천이나 투자 자문이 아니다.

## 로컬 검증 기록 (2026-08-11)

- 문서 샘플은 기존 CSV와 동일한 Header 1행과 6개월 합성 거래 37행이며 수식이 없다. 생성 후 표 범위와 수식 Scan을 검사하고 렌더링을 확인했으며 실제 `TransactionXlsxParser`로 다시 읽었다.
- 동일 합성 거래의 CSV/XLSX에 대해 `NormalizedTransaction`과 전체 `FinancialPatternAnalysisResponse`를 재귀 비교한다.
- 분석 전후 Profile Repository 개수, 최신 Snapshot ID와 History 개수가 바뀌지 않음을 통합 테스트로 검증한다.
- React 테스트는 파일명 미표시, Storage 미사용, 중복 제출 차단, 내부 Profile ID 제거, Backend 금액 문자열 유지, Warning 매핑, 선택 필드만 PUT에 반영하는 흐름을 검증한다.
- 실제 Kakao 인증 세션에서 합성 Profile v1을 만든 뒤 37개 거래 XLSX를 업로드했다. 분석 직후 Snapshot은 v1로 유지됐고, 월 고정지출 한 필드만 선택·승인한 뒤 v2가 생성되며 나머지 Profile 값과 v1 이력이 보존됨을 확인했다.
- 새로고침 뒤 파일과 분석 결과가 화면에 남지 않았고 Browser Console warning/error는 0건이었다. 1440px Desktop과 390px Mobile을 확인했으며, 결과 표는 Page가 아니라 내부 Wrapper에서만 가로 Scroll된다.
