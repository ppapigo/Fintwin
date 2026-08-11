# Production Operations Checklist

## 최초 배포 전

- [ ] `gradlew clean build`, `npm test`, `npm run build` 성공
- [ ] `docker compose -f compose.yaml config --quiet` 성공
- [ ] 운영 환경 파일을 Git 밖에서 작성하고 권한 `600` 확인
- [ ] 모든 required 변수 비어 있지 않고 기본/예제 Password가 아님
- [ ] `FRONTEND_BASE_URL`과 `CORS_ALLOWED_ORIGINS`가 동일 HTTPS Origin
- [ ] Google/Kakao callback URI가 실제 domain과 정확히 일치
- [ ] OpenAI를 쓰지 않으면 `FINTWIN_AI_ENABLED=false`; 쓰면 key가 server에만 존재
- [ ] DNS A/AAAA 연결 완료, 22는 관리자 고정 IP만, 80/443만 public
- [ ] 8080/3306 Security Group 및 host firewall 비공개
- [ ] 기존 DB/volume 여부 확인; 존재하면 승인된 backup 완료
- [ ] 디스크/RAM 확인, build 장비가 작으면 swap 계획 검토

## 배포

- [ ] `docker compose --env-file .env.prod -f compose.prod.yaml config --quiet` 성공
- [ ] 처음 생성하는 MySQL volume에만 초기 schema가 적용됨
- [ ] MySQL health 후 Backend가 시작되고 `ddl-auto=validate` 통과
- [ ] HTTP bootstrap에서 Certbot webroot 발급 성공
- [ ] 인증서/private key가 `.certbot` host 경로에만 있고 image/Git에 없음
- [ ] `NGINX_TLS_MODE=https` 재기동 후 HTTP → HTTPS 301
- [ ] 세 서비스 `healthy`, restart policy와 log rotation 확인

## 배포 후 기능/보안

```bash
docker compose --env-file .env.prod -f compose.prod.yaml ps
curl -fsS https://<DOMAIN>/actuator/health
curl -I https://<DOMAIN>/scenario-lab
curl -i https://<DOMAIN>/api/auth/csrf
curl -i https://<DOMAIN>/api/auth/me
docker compose --env-file .env.prod -f compose.prod.yaml exec frontend nginx -t
```

- [ ] React deep link가 HTML 200으로 열림
- [ ] security header(CSP, HSTS, nosniff, DENY, referrer/permissions) 확인
- [ ] 금융/API 응답 `Cache-Control: no-store`, asset 장기 cache 확인
- [ ] 익명 금융 API/XLSX 분석/template 접근 거부
- [ ] 인증 session에서 XLSX template/download/upload 2 MiB 경계 확인
- [ ] Google/Kakao 로그인, callback, 새로고침 session, logout 확인
- [ ] 변경 API가 CSRF 없이는 거부되고 발급 token으로 성공
- [ ] URL, Frontend storage, console에 token/user/profile/OAuth subject 없음
- [ ] Backend/MySQL host port 없음
- [ ] Backend 직접 접속이 인터넷에서 불가능

## 로그와 개인정보

```bash
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=200 --no-log-prefix frontend
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=200 --no-log-prefix backend
docker compose --env-file .env.prod -f compose.prod.yaml logs --tail=200 --no-log-prefix mysql
```

로그를 외부로 공유하지 않는다. 다음 항목이 없어야 한다.

- API/OAuth/OpenAI key와 Authorization/Cookie/CSRF token
- provider body/request ID, OAuth subject
- 금융 원문, 거래 description, 파일명, Profile/시뮬레이션 금융값
- SQL bind 값, stack trace, 내부 예외 message

Nginx access log는 query string이 아닌 method/path/status만 기록한다. 민감값을 query parameter로 설계하지 않는 기존 원칙도 유지한다.

Frontend bundle에는 Secret key 이름이나 Backend 절대 주소가 없어야 한다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml exec frontend \
  sh -c "grep -R -n -E 'OPENAI_API_KEY|GOOGLE_CLIENT_SECRET|KAKAO_CLIENT_SECRET|localhost:8080|backend:8080' /usr/share/nginx/html && exit 1 || exit 0"
```

실제 Secret 문자열을 grep 명령 인자나 CI log에 넣지 않는다.

## Backup/복구

- [ ] 매 배포 및 schema 변경 전에 `mysqldump --single-transaction`
- [ ] backup 파일 mode `600` 이하, 디렉터리 `700`, Git 제외
- [ ] backup 크기/생성시각/암호화 보관 위치 확인
- [ ] 정기적으로 별도 빈 DB에서 복구 연습 및 `schema_verify_mysql8.sql` 확인
- [ ] 자동 `DROP`, volume 삭제, 오래된 backup 자동 삭제 명령 없음

## 재기동과 장애 대응

- [ ] 인스턴스 reboot 후 Docker daemon과 세 서비스 자동 복구 확인
- [ ] `docker system df`, `df -h`, memory/swap 확인
- [ ] 인증서 만료일과 갱신 job 마지막 성공 확인
- [ ] unhealthy 시 `ps`, 제한된 최근 log, container inspect 순으로 확인
- [ ] Backend schema validation 실패 시 update/create로 우회하지 않고 배포 중단
- [ ] DB 장애 시 쓰기 재시도/초기화를 반복하지 않고 backup 및 volume 상태 확인

## Rollback

이 단일 인스턴스 구성은 무중단 rollback을 지원하지 않는다.

1. 장애 release 추가 재기동 중지
2. 변경 전 backup과 이전 검증 release 식별
3. Schema 호환이면 이전 image/source로 재배포
4. Schema 비호환이면 사람의 승인 후 새 빈 DB에 backup 복구
5. schema verify, health, OAuth/session/CSRF 확인

Rollback 목적으로 `docker compose down -v`, `docker volume rm`, `DROP DATABASE`를 실행하지 않는다.

## 절대 Commit 금지

- `.env.prod`, `.env.*` 실제값, local validation 환경 파일
- `.certbot/`, 인증서/private key
- `backups/`, SQL dump, volume archive
- 거래 CSV/XLSX, 금융 export, provider payload/log
- OAuth/OpenAI Secret 또는 token
