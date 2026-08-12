# Market Stress Simulation

FinTwin Market Stress Simulation은 최신 Financial Profile과 사용자가 직접 입력한 Exposure·충격 가정만으로 Baseline과 Stress 경로를 비교하는 결정론적 기능이다. 현재 시장 관측값은 별도 Context로 표시하며 미래수익률 예측, 충격값 추천 또는 계산 입력으로 사용하지 않는다. 결과는 저장하지 않고 외부 AI를 호출하지 않는다.

## 사용자 흐름

`로그인 → 최신 Profile 확인 → /market-stress → 공식 시장 Context 확인 → Exposure·충격 입력 → Baseline/Stress 실행 → Market Impact·Risk·Goal Margin 비교`

Profile이 없으면 `/profile/setup`을 안내한다. 요청과 응답에 사용자 ID, OAuth Subject, Session ID와 Profile ID를 포함하지 않고 Profile Version만 표시한다.

## 공식 시장 Context

| 지표 | 공식 출처 | Gateway | 계산 사용 |
|---|---|---|---|
| KOSPI | [KRX OPEN API](https://openapi.krx.co.kr/) KOSPI 시리즈 일별시세 | `KrxMarketDataGateway` | 사용 안 함 |
| 원/달러 환율 | [한국은행 ECOS](https://ecos.bok.or.kr/api/) `731Y001` | `BokEcosMarketDataGateway` | 사용 안 함 |
| 한국은행 기준금리 | [한국은행 ECOS](https://ecos.bok.or.kr/api/) `722Y001` | `BokEcosMarketDataGateway` | 사용 안 함 |

KRX에는 조회일과 `AUTH_KEY`만, ECOS에는 통계코드·항목코드·기간과 API Key만 전달한다. 사용자 ID, Profile, 금융금액, Exposure와 Stress 가정은 Provider에 전달하지 않는다. Provider Body·인증키·Request ID는 로그에 기록하지 않는다.

- `AVAILABLE`: 공식 응답을 파싱했고 최신성 기준 안에 있음
- `STALE`: 관측일이 기준을 지났거나 갱신 실패 시 메모리의 마지막 정상값을 사용함
- `UNAVAILABLE`: 기능 비활성화, Credential 누락, Timeout, Provider 오류, 데이터 없음 또는 안전한 파싱 실패
- 전체 Context 상태는 `AVAILABLE`, `PARTIAL`, `UNAVAILABLE` 중 하나다.

시장·환율은 기본 7일, 기준금리는 45일이 지나면 `STALE`이다. 정상값은 서버 메모리에 기본 30분만 Cache하며 DB·Redis·파일에 저장하지 않는다. 실패값을 `0`이나 임의값으로 대체하지 않고 자동 재시도하지 않는다.

## API

### `GET /api/market-stress/context`

공식 관측 상태를 반환한다. Credential이 없어도 500이나 가짜 값 대신 지표별 `UNAVAILABLE`을 반환한다.

### `POST /api/market-stress/simulate`

```json
{
  "startYearMonth": "2026-08",
  "horizonMonths": 60,
  "assumptions": {
    "annualIncomeGrowthRate": 0,
    "annualInflationRate": 0,
    "annualDepositInterestRate": 0,
    "annualInvestmentReturnRate": 0,
    "monthlyDebtPayment": 300000
  },
  "exposure": {
    "domesticStockAmount": 3000000,
    "overseasStockAmount": 2000000
  },
  "stressScenario": {
    "shockYearMonth": "2027-07",
    "domesticStockShockRate": -20,
    "overseasStockShockRate": -25,
    "krwUsdExchangeRateShockRate": 10,
    "loanInterestRateChangePercentagePoints": 2
  },
  "targetNetWorth": 50000000
}
```

- 기간: 12·36·60개월
- Exposure: 각각 0원 이상이며 합계가 최신 Profile 투자자산 이하
- 국내·해외 주식 충격: -100~0%
- 원/달러 충격: -100~100%. 양수는 원/달러 상승으로 해외 Exposure 원화가치가 증가하는 가정
- 대출금리 변화: -20~20%p. 충격 월부터 종료 월까지 적용하며 유효 금리는 0% 아래로 내리지 않음
- 충격 월: 시뮬레이션 기간 내부
- 목표 순자산: 선택값, 입력 시 0원 초과

응답은 Profile Version, 적용 가정, Baseline·Stress 월별 결과와 Checkpoint, Market Impact Breakdown, Risk, 선택적 Goal Margin, 구조화 Warning, 계산 기준과 면책을 포함한다. 결과와 요청값은 영속화하지 않는다.

## 결정론적 엔진 경계

기존 `MonthlyFinancialSimulationEngine`의 호출 계약은 유지한다. 새 `MonthlySimulationEffectProvider`는 월별로 `annualDebtInterestRateDelta`와 `investmentAssetValueAdjustment`만 전달한다. Spring, JPA, Controller, Provider와 AI 의존성은 이 경계 안으로 들어오지 않는다. Baseline 1회와 Stress 1회가 동일 엔진을 사용한다.

```text
충격 직전 Exposure = 시작 Exposure에 기존 일반 투자수익률 가정을 월별 반영
국내 영향 = 국내 충격 직전 Exposure × 국내 충격률
해외 영향 = 해외 충격 직전 Exposure × 해외 충격률
환율 영향 = (해외 충격 직전 Exposure + 해외 영향) × 환율 충격률
총 투자자산 영향 = 국내 영향 + 해외 영향 + 환율 영향
```

같은 달의 일반 투자수익률을 먼저 반영한 뒤 자산 충격을 한 번 적용한다. 향후 투자 납입액은 국내·해외로 임의 분류하지 않아 충격 대상에서 제외한다. 해외 주식과 환율 충격은 순차 적용해 동일 원금 이중 반영을 막는다. 금액은 `BigDecimal`, 소수점 2자리, `HALF_UP`을 사용한다.

## Risk와 Goal Margin

Baseline과 Stress의 현금 부족 여부·개월 수·최초 월, 음의 상환 여부·개월 수·최초 월, 최소 유동자산, 최종 부채, 누적 대출이자와 최종 순자산을 비교한다. 목표 순자산이 있으면 `최종 순자산 - 목표 순자산`으로 각 Margin을 계산한다. 목표가 없으면 `NOT_PROVIDED`이며 Margin을 생성하지 않는다.

## 개인정보·AI·영속화 경계

- Gateway는 사용자 요청 객체를 받지 않으며 Profile과 금융값을 KRX·한국은행·외부 AI에 전달하지 않는다.
- 외부 AI 호출은 0회다.
- Profile Snapshot과 Schema를 변경하지 않는다.
- 요청·응답·Context를 DB, Session, 파일, Browser Storage에 저장하지 않는다.
- Frontend는 금액을 문자열로 유지하고 Chart 좌표에만 `Number`를 사용한다.
- 새로고침하면 Stress 결과가 제거된다.

## 환경변수

`FINTWIN_MARKET_DATA_ENABLED`, `KRX_OPEN_API_KEY`, `BOK_ECOS_API_KEY`, `MARKET_DATA_CONNECT_TIMEOUT`, `MARKET_DATA_READ_TIMEOUT`, `MARKET_DATA_CACHE_TTL`, `MARKET_DATA_MAX_RESPONSE_BYTES`, `MARKET_DATA_STALE_DAYS`, `BOK_BASE_RATE_STALE_DAYS`

공식 Endpoint는 KRX와 한국은행 HTTPS Host로 제한한다. Key가 없어도 앱은 정상 기동하고 Context만 `UNAVAILABLE`이다. 실제 Key는 Git, Docker Image, Frontend Bundle과 로그에 포함하지 않는다.

## 한계와 면책

- 현재 관측값으로 미래수익률, 환율, 금리 또는 손실률을 예측하지 않는다.
- 세금, 수수료, 실제 체결가격, 종목별 가격, 배당과 자산배분 재조정은 반영하지 않는다.
- 입력 Exposure는 사용자가 확인한 현재 투자자산 분류다.
- 결과는 사용자 가정 아래 비교이며 손실 한도나 목표 달성을 보장하지 않는다.
- 금융상품 추천, 투자자문, 매수·매도 권유가 아니다.
- Stock Fit, 투자성향 분석과 종목 후보 매칭은 포함하지 않는다.
