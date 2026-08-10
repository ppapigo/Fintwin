# Financial Profile API

Financial Profile은 한 행을 수정하는 데이터가 아니라 사용자별 불변 스냅샷의 버전 집합이다. 사용자 ID는 요청에 포함하지 않으며 `CurrentUserIdProvider`에서만 얻는다. 실제 OAuth2 인증이 연결되기 전까지 금융 API는 Spring Security의 인증 보호 대상이다.

## 버전 모델

- 최초 등록은 `version: 1`, `previousProfileId: null`이다.
- 수정은 현재 행을 UPDATE하지 않고 `version: N + 1`인 새 행을 INSERT한다.
- 새 행의 `previousProfileId`는 직전 스냅샷 ID이다.
- 현재 Profile은 사용자별 가장 높은 version이다.
- `(user_id, profile_version)`은 데이터베이스에서 유일하다.

`previousProfileId`는 JPA 자기참조 대신 단순 ID로 저장한다. 현재 사용 사례는 이전 버전 식별만 필요하므로 불필요한 lazy loading과 순환 객체 그래프를 피하기 위한 선택이다.

## 요청 예시

최초 등록: `POST /api/financial-profiles`

수정(full replacement): `PUT /api/financial-profiles/current`

```json
{
  "monthlyIncome": 5000000.00,
  "cashAssets": 3000000.00,
  "deposits": 10000000.00,
  "investmentAssets": 8000000.00,
  "totalLoanBalance": 10000000.00,
  "loanInterestRate": 4.2500,
  "monthlyFixedExpenses": 1500000.00,
  "monthlyVariableExpenses": 800000.00,
  "monthlySavings": 700000.00,
  "monthlyInvestments": 500000.00
}
```

금액은 0 이상이고 필수이며 소수점 둘째 자리까지 허용한다. 대출금리는 0 이상 100 이하이며 소수점 넷째 자리까지 허용한다.

## 조회 API

- `GET /api/financial-profiles/current`: 최신 스냅샷 조회
- `GET /api/financial-profiles/me`: current의 하위 호환 별칭
- `GET /api/financial-profiles/history`: 최신 version부터 내림차순으로 전체 이력 조회
- `GET /api/financial-profiles/{profileId}`: 현재 사용자 소유의 개별 스냅샷 조회

Profile이 없거나 다른 사용자 소유의 ID이면 공통 오류 형식의 `404 Not Found`를 반환한다. 중복 최초 등록과 버전 유일성 충돌은 `409 Conflict`를 반환한다.

## 동시성과 스키마 운영

최초 생성과 수정은 사용자 행에 비관적 쓰기 잠금을 획득한 트랜잭션에서 처리한다. 따라서 애플리케이션의 정상 쓰기 경로에서는 동일 사용자의 version 계산이 직렬화된다. 복합 유일성 제약은 우회 쓰기나 예상하지 못한 경쟁을 추가로 차단한다.

Flyway를 사용하지 않는다. 신규 test 스키마는 H2 `create-drop`으로 검증하며, 기존 MySQL 스키마에는 과거 `user_id` 단일 unique 제약이 남을 수 있다. 데이터를 삭제하거나 자동 초기화하지 말고 배포 전에 해당 단일 제약 제거와 `profile_version`, `previous_profile_id`, 복합 unique 제약 반영 여부를 운영 절차에서 확인해야 한다. prod의 `ddl-auto=validate` 정책은 유지한다.
