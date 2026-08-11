# MySQL 8.4 초기화와 복구

## 적용 범위와 금지 사항

`deploy/mysql/schema_init_mysql8.sql`은 현재 영속 Entity 세 개(`users`, `oauth_identities`, `financial_profiles`)와 Index, Unique, FK, collation을 반영한다. Financial Profile은 `(user_id, profile_version)`이 유일한 불변 snapshot이다.

이 SQL은 새롭고 비어 있는 DB 전용이다. 기존 데이터가 있는 DB에 “맞춰 보기” 위해 실행하지 않는다. `DROP`, `TRUNCATE`, 데이터 삭제 또는 자동 rollback은 포함하지 않는다. Schema 변경은 반드시 별도 검토·백업 후 명시적 SQL로 진행한다.

## Compose 최초 초기화

`compose.prod.yaml`은 SQL을 MySQL의 `/docker-entrypoint-initdb.d/001-schema-init.sql`에 read-only로 연결한다. MySQL 공식 entrypoint는 named volume이 완전히 비어 있는 최초 1회에만 이 디렉터리를 실행한다. 기존 `mysql_data` volume에는 재실행하지 않는다.

최초 기동 전 빈 DB 여부를 확인한다. 기존 volume이 보이면 삭제하지 말고 중단하여 소유자와 백업을 확인한다.

```bash
docker volume ls | grep fintwin-prod_mysql_data
docker compose --env-file .env.prod -f compose.prod.yaml up -d mysql
docker compose --env-file .env.prod -f compose.prod.yaml ps mysql
```

## 수동 적용과 검증

Compose 자동 초기화를 사용하지 않는 빈 MySQL 8.4에서는 다음처럼 적용한다. 명령은 container 환경변수를 참조하므로 Password를 인자나 출력에 직접 쓰지 않는다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' \
  < deploy/mysql/schema_init_mysql8.sql

docker compose --env-file .env.prod -f compose.prod.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' \
  < deploy/mysql/schema_verify_mysql8.sql
```

검증 결과에는 정확히 `users`, `oauth_identities`, `financial_profiles`가 있어야 하고 orphan count는 0, 중복 profile version 결과는 없어야 한다. 이어서 Backend가 `prod` profile과 `ddl-auto=validate`로 healthy가 되는지 확인한다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d backend
docker compose --env-file .env.prod -f compose.prod.yaml ps backend
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=100 backend
```

Schema와 Entity가 다르면 Backend는 기동 실패해야 한다. 이 실패를 `ddl-auto=update`로 우회하지 않는다.

## Backup

Dump에는 사용자 금융정보가 포함된다. 서버 전용 `backups` 디렉터리를 만들고 권한을 제한한다.

```bash
install -d -m 700 backups
umask 077
docker compose --env-file .env.prod -f compose.prod.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers "$MYSQL_DATABASE"' \
  > "backups/fintwin-$(date -u +%Y%m%dT%H%M%SZ).sql"
```

배포 전과 모든 Schema 변경 전에 dump를 생성하고, 크기가 0이 아닌지와 제한된 권한을 확인한다. Backup 파일/경로를 Git, 채팅, 일반 object storage에 올리지 않는다. 장기 보관 시 접근통제와 암호화를 별도로 적용한다.

Named volume의 raw 파일 복사보다 일관성 있는 `mysqldump`를 MVP 기본으로 사용한다. Container/volume 자체 backup을 추가할 때도 MySQL 정합성과 암호화를 검증한다.

## 복구와 Rollback

복구 대상은 새로 만든 빈 database로 준비하고, 적용 전에 사람의 명시적 승인을 받는다. 자동 삭제 명령은 제공하지 않는다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE"' \
  < backups/<approved-backup>.sql
```

복구 후 `schema_verify_mysql8.sql`, Backend `ddl-auto=validate`, 사용자 수/profile snapshot 수를 확인한다. 애플리케이션 rollback은 이전 검증 release를 재배포하고, DB rollback이 필요한 경우에만 승인된 backup을 새 DB에 복구한다. 무조건적인 volume 삭제나 `DROP DATABASE`를 rollback으로 사용하지 않는다.
