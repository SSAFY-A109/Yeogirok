# 소셜 로그인(OIDC) 설계

> 여기록 프론트 계약 초안(`frontend/docs/api-contract.md`, 팀 Notion 2026-10-27 초안)을 기준으로 정리했다.
> Google·Kakao의 OIDC 엔드포인트·클레임 값은 구현 전에 §6 체크리스트대로 공식 문서에서 다시 확인한다.

## 1. 명세 초안이 가정한 흐름

`frontend/docs/api-contract.md` §1·§2:

| ID | Method | URL | 쓰임 |
| --- | --- | --- | --- |
| API-001 | GET | `/auth/{provider}/login` | 소셜 로그인 시작 |
| API-002 | GET | `/auth/{provider}/callback` | 소셜 로그인 콜백 |
| API-005 | POST | `/auth/token` | 로그인 완료 및 서비스 토큰 발급 |
| API-006 | POST | `/auth/token/refresh` | 서비스 토큰 재발급 |
| API-007 | POST | `/auth/logout` | 로그아웃 |

(Base path `/api/v1`, provider는 `google`·`kakao`. API-003·004는 비어 있다.)

API-001·002가 서버에 있으므로 **서버 주도 리다이렉트 방식**을 전제로 한 명세다. 콜백에서 토큰을 바로 주지 않고 API-005로 한 번 더 교환하는 구조라서, URL로 토큰을 넘기는 문제를 피하려는 의도로 보인다.

## 2. 선택지

| | A. 서버 주도 OIDC (명세 초안 그대로) | B. 네이티브 SDK + ID 토큰 검증 |
| --- | --- | --- |
| 앱 | `expo-web-browser` `openAuthSessionAsync`로 API-001을 열고 딥링크로 돌아옴. 네이티브 모듈 추가 없음(이미 설치됨) | 구글 `@react-native-google-signin/google-signin`, 카카오 `@react-native-seoul/kakao-login` 등 네이티브 모듈 2개 + config plugin. 개발 빌드 필수 |
| 서버 | Spring `oauth2-client`(`oauth2Login`) + 인가 요청 저장소 + 일회용 코드 발급·교환 | `oauth2-resource-server`의 `JwtDecoder` 2개(구글·카카오) + `POST /auth/token { provider, idToken }` |
| API | 001·002·005 모두 필요 | 001·002 불필요. 005만 남음 → **명세 변경** |
| 비밀값 | 구글·카카오 client secret을 서버만 가짐 | client secret 불필요(공개 클라이언트) |
| UX | 인앱 브라우저 화면이 뜸. 카카오톡 앱 간편 로그인은 브라우저 경유 | 구글 계정 선택 시트, 카카오톡 앱 간편 로그인이 네이티브로 동작 |
| 구현량(서버) | 많음 | 적음 |

**결정(2026-10-28, 백엔드 제안 — 팀 확정 전)**: **B안으로 간다.** 서버가 단순해 보안 실수 여지가 적고, 네이티브 계정 선택·카카오톡 간편 로그인으로 UX가 낫다. 명세 변경안은 `04-auth-api.md`에 있다. 프론트 계약 사본(`frontend/docs/api-contract.md`)은 Notion이 바뀐 뒤 프론트가 갱신한다.

A안 설계(§3-1)는 팀이 B를 받아들이지 않을 때를 대비해 남겨 둔다. 어느 쪽이든 **ID 토큰 검증 → `(provider, sub)`로 회원 조회 → 서비스 JWT 발급** 이후 단계는 같다(§3-3 이후).

## 3. 설계

### 3-1. (보류) A 방식 흐름 (서버 주도 OIDC + 일회용 코드)

```
앱  ① code_verifier 생성, code_challenge = S256(verifier)
    ② openAuthSessionAsync(
         "{API}/api/v1/auth/{provider}/login?redirect_uri=yeogirok://auth/callback&code_challenge=…",
         "yeogirok://auth/callback")
서버 API-001  redirect_uri를 허용 목록과 대조 → 인가 요청(state·nonce·서버 PKCE) + 앱 code_challenge 저장
              → 302 제공자 authorize (scope에 openid 포함)
제공자 → 서버 API-002 /auth/{provider}/callback?code&state
서버  state 확인 → 토큰 교환 → **ID 토큰 검증**(서명·iss·aud·exp·nonce)
      → (provider, sub)로 회원 조회·생성
      → 일회용 로그인 코드 발급(랜덤, 60초, 1회용, 앱 code_challenge와 묶어 저장)
      → 302 yeogirok://auth/callback?code=<일회용 코드>
앱  ③ API-005 POST /auth/token { code, codeVerifier }
서버  코드 소비 + S256(codeVerifier) == code_challenge 확인 → 서비스 access·refresh 발급
```

- Spring `oauth2Login`에 `scope: openid, …`를 주면 `OidcAuthorizationCodeAuthenticationProvider`가 ID 토큰 서명·`iss`·`aud`·`exp`·`nonce`를 검증한다. 제공자 등록에 `issuer-uri`를 주면 discovery로 JWKS를 찾는다.
- `authorizationEndpoint.baseUri("/api/v1/auth")` + `redirectionEndpoint.baseUri("/api/v1/auth/*/callback")`로 API-001·002 경로에 맞출 수 있다. `{provider}/login` 형태는 커스텀 `OAuth2AuthorizationRequestResolver`가 필요할 수 있다(구현 시 확인).
- STATELESS이므로 인가 요청 저장소가 필요하다. 쿠키보다 Redis(키 = `state`, TTL 3분)에 두는 편이 쿠키 크기·직렬화 문제가 없다.
- **`redirect_uri`는 허용 목록과 대조한다.** 검증하지 않으면 공격자 주소로 결과가 나가는 오픈 리다이렉트가 된다.
- **앱 쪽 PKCE(code_challenge/verifier)**: 커스텀 스킴(`yeogirok://`)은 다른 앱이 가로챌 수 있으므로 딥링크로 받은 일회용 코드만으로는 토큰을 주지 않는다.
- 콜백·딥링크 URL에 서비스 토큰을 절대 싣지 않는다. 브라우저 기록·서버 로그·Referer로 샐 수 있다.
- 실패 시에도 딥링크로 돌려보낸다: `yeogirok://auth/callback?error=<code>`(예: `AU001`). 문구가 아니라 코드를 넣는다.

### 3-2. B 방식 (채택) — API 상세는 `04-auth-api.md`

```java
public interface OidcIdTokenVerifier {          // 제공자마다 구현 1개
    OAuthProvider provider();
    OidcIdentity verify(String idToken, String nonce);   // sub, email(선택), nickname(선택)
}
```

- 제공자별 `JwtDecoder` 빈: `NimbusJwtDecoder.withJwkSetUri(...)` + `JwtTimestampValidator` + `iss` + `aud`(허용 목록) + `nonce` 검증.
- `allowed-audiences`는 리스트로 받아 웹·Android·iOS 클라이언트 ID를 함께 허용할 수 있게 한다.
- `POST /auth/token { provider, idToken, nonce }` → `Map<OAuthProvider, OidcIdTokenVerifier>`로 분기.
- 앱이 로그인 직전 nonce를 만들어 SDK에 넘기고, 같은 값을 서버에 보낸다. 탈취한 ID 토큰을 유효 기간 안에 재사용하는 공격을 막는다.
- 별도 JWT 라이브러리 없이 Spring Security(`oauth2-resource-server`, `oauth2-jose`)만으로 ID 토큰 검증과 서비스 JWT를 모두 처리한다.

### 3-3. 서비스 JWT·인가 (A·B 공통)

- `oauth2-resource-server` + HS256 `JwtEncoder`/`JwtDecoder`. 클레임 `iss`·`sub`(userId)·`iat`·`exp`·`jti`·`role`.
- 설정값은 `@ConfigurationProperties` + `@Validated` record로 받아 누락 시 기동 단계에서 실패하게 한다.
- 리프레시 토큰은 JWT가 아닌 랜덤 문자열로 만들고, SHA-256 해시를 키로 Redis에 두며 재발급 때 회전한다. 로그인 한 번을 세션 하나로 묶으므로 여러 기기 로그인이 가능하다. 다기기 허용 여부는 **팀 결정 필요**.
- 회전된 이전 리프레시 토큰이 다시 오면 탈취로 보고 그 세션을 폐기한다(재사용 감지).
- **401 세 갈래**(명세 C004·C005·C006): 커스텀 `AuthenticationEntryPoint`에서

  | 조건 | code |
  | --- | --- |
  | `Authorization` 헤더 없음(또는 `Bearer ` 아님) | C004 |
  | 원인이 `JwtValidationException`이고 오류 중 `JwtTimestampValidator`의 만료 오류가 있음 | C005 |
  | 그 외 `InvalidBearerTokenException`·서명 실패·클레임 실패 | C006 |

  만료 판별은 `JwtValidationException.getErrors()`의 `OAuth2Error` 설명(`Jwt expired at …`)에 기대게 되므로, 구현 시 Spring Security 버전의 실제 값을 테스트로 고정한다.
- 403은 `AccessDeniedHandler` + `AuthorizationDeniedException` 핸들러 모두 **C007**.
- principal은 `AuthenticatedUser(userId)` record. 컨트롤러는 `@AuthenticationPrincipal AuthenticatedUser user`.

### 3-4. 회원 모델

ERD 초안의 `users` 테이블을 따른다(확정 전).

| 컬럼 | 비고 |
| --- | --- |
| `provider` | `GOOGLE`·`KAKAO` (`auth_provider` 타입) |
| `provider_subject` | ID 토큰 `sub`. `(provider, provider_subject)` 유니크 |
| `email` | 선택. 카카오는 동의 항목이라 null 가능. **식별·계정 병합에 쓰지 않는다** |
| `nickname` | NOT NULL. 최초 가입 시 ID 토큰에서 채운다. 클레임이 없으면 기본값 `여행자` |
| `profile_image_url` | 최초 가입 시 ID 토큰에서 채우고 이후 사용자가 수정 |
| `status`, `withdrawn_at` | `ACTIVE`·`WITHDRAWN`. 탈퇴(API-009) 처리 때 쓴다 |
| `created_at`, `updated_at` | |

- ERD에 `role` 컬럼이 없어 저장하지 않는다. 액세스 토큰의 `role` 클레임은 지금 모두 `USER`다. GUEST 구분은 여기록 비회원 범위(DEC-19)와 함께 결정한다.
- 테스트는 `src/test/resources/schema.sql`로 테이블을 만든다. 로컬은 `ddl-auto: update`. 스키마 관리 도구는 ERD 확정 뒤에 정한다.

- 회원 식별 키는 이메일이 아니라 `(provider, sub)`다. 이메일 변경·미동의에 흔들리지 않는다.
- 같은 사람이 구글·카카오로 각각 가입하면 별개 계정이 된다(이메일 자동 병합 금지 — 미검증 이메일로 계정 탈취 가능). 계정 연결 기능은 명세에 없다.

### 3-5. 탈퇴(API-009)와 제공자 연결 끊기

- 카카오: 앱 Admin 키로 `POST https://kapi.kakao.com/v1/user/unlink`(`target_id_type=user_id`, `target_id=sub`)를 부르면 사용자 토큰 없이 끊을 수 있다. **서버에서 해제한다**(2026-11-04 결정, `04-auth-api.md` §3 API-009). 실제 카카오 호출은 아직 확인하지 않았다.
- 구글: 토큰 폐기(`https://oauth2.googleapis.com/revoke`)에는 구글 토큰이 필요한데 서버는 구글 토큰을 저장하지 않는다. 그래서 **탈퇴할 때만** 앱이 재로그인으로 받은 `serverAuthCode`를 보내고, 서버가 토큰으로 바꿔 바로 해제한다(2026-11-04 결정). 이때만 웹 클라이언트 시크릿을 쓴다.

## 4. 의존성

| 용도 | A안 | B안(채택) |
| --- | --- | --- |
| 소셜 로그인 | `spring-boot-starter-oauth2-client` | 불필요 |
| ID 토큰·서비스 JWT 검증 | `spring-boot-starter-oauth2-resource-server` | 같음 |
| 서비스 JWT 발급 | `NimbusJwtEncoder` | 같음 |
| 인가 요청·일회용 코드 저장 | Redis | 불필요 |

## 5. 열린 결정

- 다기기 로그인 허용 여부(리프레시 토큰 저장 단위), 액세스·리프레시 TTL
- 비회원 범위(DEC-19)와 GUEST→USER 전환 조건
- 구글 연결 끊기(revoke) 정책

## 6. 구현 전 확인 체크리스트

- [ ] 팀 확정: B(네이티브 SDK) — Notion 명세 반영(API-001·002 폐기, API-005 요청 변경)
- [ ] 프론트 합의: 구글·카카오 네이티브 SDK 도입(개발 빌드, config plugin), 두 SDK의 nonce 지원 여부
- [ ] API-005·006·007 요청·응답 상세, `AU` 도메인 오류 코드(Notion 명세)
- [ ] 다기기 로그인 허용 여부(리프레시 토큰 저장 단위), 액세스·리프레시 TTL
- [ ] 비회원 범위(DEC-19)와 GUEST→USER 전환 조건
- [ ] Google: issuer 값(`https://accounts.google.com`·`accounts.google.com`), JWKS URI, discovery 문서 — 공식 문서 확인
- [ ] Kakao: 콘솔에서 **OpenID Connect 활성화**, issuer(`https://kauth.kakao.com`), JWKS(`https://kauth.kakao.com/.well-known/jwks.json`), discovery(`/.well-known/openid-configuration`), ID 토큰 `aud`가 어떤 앱 키인지(REST API 키 / 네이티브 앱 키), 닉네임·프로필·이메일 클레임과 동의 항목 — 공식 문서 확인
- [ ] Kakao `client_secret` 사용 여부와 Spring 등록의 `client-authentication-method`
- [ ] Kakao Admin 키 연결 끊기, Google revoke 정책
- [ ] 딥링크 스킴(`app.json`의 `scheme`)과 `redirect_uri` 허용 목록 — 프론트와 합의
- [ ] 만료(C005) 판별에 쓰는 `JwtValidationException` 오류 값 — 사용하는 Spring Security 버전에서 테스트로 고정
