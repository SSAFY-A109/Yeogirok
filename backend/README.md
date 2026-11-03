# 여기록 백엔드

Spring Boot 4.0.8 · Java 21 · Gradle · PostgreSQL · Redis · Spring Security(리소스 서버).

## 실행

로컬에 PostgreSQL과 Redis가 떠 있어야 한다. 레포 루트에서 `docker compose -f infra/docker-compose.local.yml up -d`로 켠다. 접속 정보 기본값은 `application-local.yaml`에 있고 `.env`의 `DB_*`·`REDIS_*`로 바꿀 수 있다.

```bash
cp .env.example .env        # 값 채우기 — docs/05-social-login-setup.md
./gradlew bootRun           # http://localhost:8080/swagger-ui.html
./gradlew test              # DB는 H2(PostgreSQL 모드). Redis 테스트는 Testcontainers 라 Docker가 켜져 있어야 한다
```

## 구조

```
src/main/java/com/ssafy/yeogirok/
├── domain/
│   ├── auth/
│   │   ├── controller/   API-005·006·007
│   │   ├── token/        서비스 액세스 토큰(HS256 JWT), 리프레시 토큰(불투명 문자열, Redis에 해시로 저장·회전)
│   │   └── service/
│   └── user/             회원 (provider, sub) 식별
└── global/
    ├── oauth/        구글·카카오 공통. OAuthProvider(제공자 구분)
    │   └── oidc/     구글·카카오 ID 토큰 검증 (JWKS · iss · aud · exp · nonce)
    ├── response/     공통 봉투 { success, data } / { success, code, message }
    ├── exception/    ExceptionType(오류 코드), GlobalExceptionHandler
    ├── idempotency/  공통 멱등키(Redis)
    └── security/     401 세 갈래(A001·A002·A003), 403(A004)
```

## 문서

| 문서 | 내용 |
| --- | --- |
| [docs/01-common-response.md](docs/01-common-response.md) | 공통 응답 모양, 오류 코드, 예외 매핑 |
| [docs/02-idempotency.md](docs/02-idempotency.md) | Redis 연결과 공통 멱등키 처리 |
| [docs/03-oidc-login.md](docs/03-oidc-login.md) | 구글·카카오 소셜 로그인(OIDC) 방식 선택과 설계 |
| [docs/04-auth-api.md](docs/04-auth-api.md) | 인증 API 설계 — B안(네이티브 SDK + ID 토큰 검증) |
| [docs/05-social-login-setup.md](docs/05-social-login-setup.md) | 구글·카카오 콘솔 설정과 서버 환경 변수 |

## 계약 원천

팀 Notion "API 명세"가 원천이다. 서버에 반영된 API는 Swagger가 가장 정확하다.
