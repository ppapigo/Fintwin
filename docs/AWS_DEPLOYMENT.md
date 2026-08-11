# AWS 단일 인스턴스 배포

이 문서는 해커톤/MVP용 EC2 또는 Lightsail 한 대에서 Nginx + Spring Boot + MySQL을 Docker Compose로 실행하는 절차다. AWS 리소스 생성은 사용자가 콘솔/CLI에서 직접 수행하며 이 저장소는 과금 리소스를 만들지 않는다.

## 인스턴스와 네트워크

- 최소 2 GiB RAM을 검토한다. 이미지 build까지 같은 장비에서 하면 4 GiB 또는 임시 swap이 더 안전하다.
- 충분한 EBS/SSD 여유를 두고 `docker system df`, `df -h`를 운영 점검에 포함한다.
- 인스턴스 OS는 지원 중인 Ubuntu LTS 등으로 선택하고 보안 업데이트를 적용한다.
- Security Group inbound:
  - TCP 22: 관리자 고정 공인 IP/32만
  - TCP 80: `0.0.0.0/0`, IPv6 사용 시 `::/0`
  - TCP 443: `0.0.0.0/0`, IPv6 사용 시 `::/0`
  - TCP 8080: 규칙 없음
  - TCP 3306: 규칙 없음
- host firewall도 8080/3306을 허용하지 않는다.

Root SSH login과 PasswordAuthentication은 비활성화를 권장한다. SSH private key는 로컬에서만 보관하고 권한을 제한한다. Docker group은 root 상당 권한이므로 배포 사용자만 포함하고 계정을 공유하지 않는다.

## DNS와 파일 배치

Elastic IP/고정 Lightsail IP를 연결하고 `DOMAIN` A/AAAA 레코드가 해당 주소로 확인된 뒤 Certbot을 실행한다. DNS가 전파되기 전에 인증서 발급을 반복하지 않는다.

```bash
sudo install -d -m 750 -o "$USER" -g "$USER" /opt/fintwin
cd /opt/fintwin
# 승인된 release source를 배치
cp .env.prod.example .env.prod
chmod 600 .env.prod
mkdir -p .certbot/etc
chmod 700 .certbot .certbot/etc
```

`.env.prod`에 실제값을 서버에서 입력한다. Production에서는 다음 세 값이 같은 HTTPS Origin이어야 한다.

```text
DOMAIN=<DOMAIN>
FRONTEND_BASE_URL=https://<DOMAIN>
CORS_ALLOWED_ORIGINS=https://<DOMAIN>
```

## HTTPS: Nginx + Certbot 선택

단일 인스턴스 MVP는 ALB/CloudFront 대신 Nginx + Certbot webroot 방식을 사용한다. 인증서와 private key는 host `.certbot/etc`에만 존재하고 이미지/Git에 넣지 않는다.

최초 발급 동안만 `.env.prod`의 `NGINX_TLS_MODE=http`로 Nginx를 시작한다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --build
docker compose --env-file .env.prod -f compose.prod.yaml ps
```

그 다음 공식 Certbot container를 일회성으로 실행한다. 사용 전 조직에서 검증한 Certbot image tag/digest로 고정한다.

```bash
docker run --rm \
  -v "$(pwd)/.certbot/etc:/etc/letsencrypt" \
  -v "fintwin-prod_certbot_webroot:/var/www/certbot" \
  certbot/certbot certonly --webroot -w /var/www/certbot \
  -d "<DOMAIN>" --email "<ADMIN_EMAIL>" --agree-tos --no-eff-email
```

발급 성공 후 `NGINX_TLS_MODE=https`로 변경하고 Frontend를 재생성한다.

```bash
docker compose --env-file .env.prod -f compose.prod.yaml up -d --force-recreate frontend
curl -I http://<DOMAIN>/
curl -I https://<DOMAIN>/
curl -fsS https://<DOMAIN>/actuator/health
```

HTTP는 HTTPS로 301 이동해야 한다. localhost에서는 이 발급 절차를 실행하지 않는다.

인증서 갱신은 같은 두 volume을 연결해 `certbot renew`를 실행한 뒤 Nginx reload를 수행한다. systemd timer/cron을 사용할 경우 명령, 실행 사용자, 실패 알림을 운영자가 관리하고 staging으로 먼저 검증한다.

```bash
docker run --rm \
  -v "$(pwd)/.certbot/etc:/etc/letsencrypt" \
  -v "fintwin-prod_certbot_webroot:/var/www/certbot" \
  certbot/certbot renew --webroot -w /var/www/certbot
docker compose --env-file .env.prod -f compose.prod.yaml exec frontend nginx -s reload
```

## OAuth Provider 등록과 E2E

Google/Kakao 콘솔에 정확히 다음 callback을 등록한다.

```text
https://<DOMAIN>/login/oauth2/code/google
https://<DOMAIN>/login/oauth2/code/kakao
```

배포 후 각각 다음을 실제 브라우저로 확인한다.

1. Google/Kakao 로그인 시작 URL이 같은 domain인지
2. Provider callback이 HTTPS로 돌아오고 `/auth/callback` 성공 화면으로 이동하는지
3. `JSESSIONID`가 Secure, HttpOnly, SameSite=Lax, Path=/인지
4. 새로고침 후 session 유지, logout 후 session 만료
5. CSRF token 없이 변경 요청이 거부되고 정상 token 요청은 성공하는지
6. URL/console/network response에 token, OAuth subject, user/profile ID가 노출되지 않는지

`X-Forwarded-Proto`를 Spring이 HTTPS로 해석하는 것은 Backend가 Nginx 뒤에서만 접근 가능하다는 전제다. Compose는 Backend port를 publish하지 않으며 Security Group도 8080을 닫아야 한다.

## 재부팅, 시간, 운영

Compose 서비스는 `restart: unless-stopped`를 사용한다. Docker daemon의 boot enable 여부와 실제 인스턴스 reboot 후 세 서비스 health를 확인한다. 시스템 로그 기준은 UTC를 권장하고 서비스 표시 timezone(`TZ=Asia/Seoul`)과의 차이를 runbook에 명시한다.

재배포는 `DOCKER_PRODUCTION.md`, backup/restore는 `DATABASE_INITIALIZATION.md`, 환경변수는 `PRODUCTION_ENVIRONMENT.md`, 최종 점검은 `OPERATIONS_CHECKLIST.md`를 따른다. 이 MVP는 multi-AZ, RDS, autoscaling, managed secret rotation, 무중단 배포를 제공하지 않는다.
