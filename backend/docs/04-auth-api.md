# 인증 API 설계안 — B안(네이티브 SDK + ID 토큰 검증)

> 작성 2026-10-28. **상태: B안 Notion 명세 반영, 서버 구현됨. 토큰 수명은 결정 필요.** 팀 Notion "API 명세"가 원천이며 이 문서는 승인된 변경과 기존 명세의 충돌 처리 기준을 기록한다.
> 배경과 A안 비교는 `03-oidc-login.md` §2. 응답 봉투·공통 코드는 `01-common-response.md` §1·§3.

## 1. 흐름

```
앱  ① nonce 생성(랜덤 32바이트 이상, base64url)
    ② 구글/카카오 SDK 로그인(nonce 전달) → ID 토큰
    ③ POST /api/v1/auth/token { provider, idToken, nonce }
서버 ④ provider별 JWKS로 서명 검증 + iss · aud · exp · nonce 확인
    ⑤ (provider, sub)로 회원 조회, 없으면 생성
    ⑥ 서비스 access·refresh 토큰 발급(refresh는 해시를 키로 Redis에 저장)
    ← { success: true, data: { …토큰… } }
앱  ⑦ access 토큰은 Authorization: Bearer, 만료(C005) 시 API-006으로 재발급
```

## 2. 엔드포인트 변경

| ID | 변경 | Method | URL | 토큰 필요 |
| --- | --- | --- | --- | --- |
| API-001 | **폐기** | GET | `/auth/{provider}/login` | — |
| API-002 | **폐기** | GET | `/auth/{provider}/callback` | — |
| API-005 | **요청 변경**: 소셜 ID 토큰으로 서비스 토큰 발급 | POST | `/auth/token` | 아니오 |
| API-006 | 상세 확정 | POST | `/auth/token/refresh` | 아니오 |
| API-007 | 상세 확정 | POST | `/auth/logout` | 예 |

Base path는 `/api/v1`. API-001·002의 폐기 사유는 "네이티브 SDK 로그인으로 전환(2026-10-28)"이다. 폐기한 ID는 다른 API에 다시 쓰지 않는다(오류 코드 재사용 금지 규칙과 같은 취지). 기존 Notion의 요청·응답·오류 코드 행은 폐기 명세 영역에 원문으로 보존한다.

## 3. 상세

### API-005 `POST /auth/token`

요청

```json
{
  "provider": "google",
  "idToken": "eyJhbGciOiJSUzI1NiIs...",
  "nonce": "q3V1b2...base64url"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `provider` | string | 예 | `google` · `kakao` |
| `idToken` | string | 예 | 제공자 SDK가 준 OIDC ID 토큰 원문 |
| `nonce` | string | 예 | 앱이 로그인 직전 만들어 SDK에 넘긴 값. ID 토큰의 `nonce` 클레임과 같아야 한다. 구글·카카오 SDK 모두 nonce가 토큰에 그대로 들어가는 것을 실기기에서 확인했다(2026-11-04) |

응답 `200`

공통 봉투 `{ "success": true, "data": { ... } }`의 data 필드는 아래와 같다. 리프레시 토큰 수명은 미정이므로 전체 응답에 임의의 수명 숫자를 넣지 않는다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `accessToken` | string | 서비스 액세스 토큰(JWT) |
| `accessTokenExpiresIn` | number | 액세스 토큰 수명(초). 기존 예시 3600초 보존 |
| `refreshToken` | string | 서비스 리프레시 토큰 |
| `refreshTokenExpiresIn` | number | 리프레시 토큰 수명(초). 결정 필요 |
| `isNewUser` | boolean | 이번 요청으로 가입됐는지. 온보딩 화면 분기용 |

기존 Notion의 expiresIn 예시는 3600초이므로 accessTokenExpiresIn 예시에도 보존한다. 30분 / 14일은 제안값이며 확정하지 않는다. 3600초 역시 기존 예시값으로 정책 확정을 뜻하지 않는다.

실패

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | C001 | `provider`·`idToken`·`nonce` 누락 |
| 400 | C003 | `provider`가 `google`·`kakao`가 아님 |
| 401 | A005 | ID 토큰 검증 실패(서명·발급자·대상·만료) |
| 401 | A006 | `nonce` 불일치(ID 토큰에 `nonce`가 없는 경우 포함) |

AU001·AU002는 이미 다른 의미로 사용 중이므로 재사용하지 않는다. 기존 loginCode·codeVerifier 요청과 tokenType·expiresIn·user 응답 필드, AU015·AU016 실패 코드는 폐기 명세로 보존하며 재사용하지 않는다.

### API-006 `POST /auth/token/refresh`

요청

```json
{ "refreshToken": "eyJ..." }
```

응답 `200` — API-005와 같은 `data`에서 `isNewUser`만 뺀 형태. **리프레시 토큰도 새로 발급(회전)**하며, 이전 리프레시 토큰은 즉시 무효가 된다.

실패

| HTTP | code | 상황 |
| --- | --- | --- |
| 400 | C001 | `refreshToken` 누락 |
| 401 | A007 | 모르는 리프레시 토큰(위변조, 만료돼 지워진 토큰 포함) → 토큰 삭제 후 로그인 화면 |
| 401 | A008 | 서비스 리프레시 토큰 만료 → 토큰 삭제 후 로그인 화면 |
| 401 | A009 | 서비스 리프레시 토큰 폐기(로그아웃·탈퇴·재사용 감지 뒤) → 토큰 삭제 후 로그인 화면 |
| 401 | A010 | 회전 후 이전 리프레시 토큰 재사용. 그 세션을 폐기한다 → 토큰 삭제 후 로그인 화면 |

리프레시 토큰은 Redis에 토큰 만료 시점까지만 두므로, 만료된 토큰은 키가 지워진 뒤에는 A007로 응답한다. A008은 만료 직후 키가 지워지기 전에만 난다. 앱은 A007~A010을 모두 같은 방식(토큰 삭제 후 로그인 화면)으로 처리한다. 기존 tokenType·expiresIn 응답 필드는 폐기로 보존하고, 새 수명 필드를 사용한다.

### API-007 `POST /auth/logout`

요청 — `Authorization: Bearer <액세스 토큰>`

```json
{ "refreshToken": "eyJ..." }
```

응답 `200` — `{ "success": true, "data": null }`. 현재 기기 세션의 리프레시 토큰만 폐기하며 다른 기기 세션은 유지한다. 이미 폐기된 토큰이어도 성공으로 응답한다(멱등). 앱은 비공개 데이터 캐시와 토큰을 즉시 지운다. 초기 구현에서는 로그아웃한 세션의 기존 액세스 토큰을 서버에서 즉시 폐기하지 않고 만료까지 유효하게 둔다. 이전 `data: { loggedOut: true }` 응답과 필드 행은 Notion의 폐기 명세에 보존한다.

실패: 공통 C004·C005·C006(액세스 토큰).

## 4. 오류 코드 (기존 정의 보존 및 충돌 처리)

| code | HTTP | 뜻 | 앱 처리 |
| --- | --- | --- | --- |
| AU001 | 400 | 지원하지 않는 인증 provider — 구 흐름 폐기 | 재사용 금지; API-005는 C003 사용 |
| AU002 | 400 | 허용되지 않은 로그인 복귀 경로 — 폐기 | 재사용 금지 |
| AU003 | 503 | 인증 제공자 일시 장애 — 구 흐름 폐기 | 재사용 금지 |
| AU017 | 401 | 서비스 리프레시 토큰 만료 — 폐기, A008로 대체 | 재사용 금지 |
| AU018 | 401 | 서비스 리프레시 토큰 폐기 — 폐기, A009로 대체 | 재사용 금지 |
| AU019 | 401 | 회전 후 이전 리프레시 토큰 재사용 — 폐기, A010으로 대체 | 재사용 금지 |

Notion에 AU001~AU021이 이미 정의되어 있다. API-001·002 전용 AU001~AU014·AU021과 API-005의 loginCode 전용 AU015·AU016은 기존 의미를 보존한 채 폐기로 표시한다. AU020(최근 재인증 필요)은 범위 밖이므로 유지한다. 소셜 ID 토큰 검증 실패(A005)·nonce 불일치(A006)·그 밖의 리프레시 토큰 무효(A007)는 새 A 코드로 배정했다. 기존 행·코드를 삭제하거나 번호를 재사용하지 않는다.

## 5. 서버 검증 기준

| 항목 | Google | Kakao |
| --- | --- | --- |
| JWKS | `https://www.googleapis.com/oauth2/v3/certs` | `https://kauth.kakao.com/.well-known/jwks.json` |
| `iss` | `https://accounts.google.com` 또는 `accounts.google.com` | `https://kauth.kakao.com` |
| `aud` | 허용 목록(웹 클라이언트 ID — RN 구글 SDK는 `webClientId` 기준으로 발급) | 허용 목록(네이티브 앱 키) |
| `exp` | 시계 오차 허용 60초 | 같음 |
| `nonce` | 필수, 요청값과 일치 | 같음 |
| 회원 키 | `sub` | `sub`(회원번호) |
| 사전 설정 | Google Cloud에 Android·iOS·웹 클라이언트 ID 생성 | 카카오 콘솔 **OpenID Connect 활성화**, 플랫폼(Android 키 해시·iOS 번들 ID) 등록 |

JWKS·`iss`·`aud`는 구글·카카오 실기기 로그인으로 확인했다(2026-11-04).

## 6. 확정 정책 및 미정 사항

### 확정 정책(2026-10-28, 명세 기준·미구현)

- 다기기 동시 로그인을 허용한다. 리프레시 토큰은 기기별 세션 단위로 관리하며, 새 기기 로그인이 기존 기기 세션을 종료하지 않는다. 토큰 회전은 해당 세션 안에서 수행한다. 저장은 Redis다(Notion ERD › Redis §2: `auth:refresh`·`auth:session`·`auth:user-sessions`).
- 로그아웃은 현재 기기 세션의 리프레시 토큰만 즉시 폐기한다. 기존 액세스 토큰은 만료까지 유효하며 앱에서 즉시 삭제한다.
- 탈퇴(API-009)는 서비스 계정 비활성화와 모든 기기 세션 폐기를 먼저 처리하고, 제공자 연결 해제를 수행한다. 연결 해제 실패가 서비스 탈퇴를 막지 않으며 서버에서 실패 작업을 보관해 재시도한다. 제공자별 SDK/API 선택 등 구체적인 구현은 확인 후 정한다.
- 액세스·리프레시 토큰 수명 및 로그인 유지 기간 정책은 이번 결정에서 변경하거나 확정하지 않는다.

### 미정 사항

- 액세스·리프레시 TTL(제안 30분 / 14일; 기존 액세스 예시 3600초 보존)
- 비회원 범위(DEC-19), 온보딩 완료 전 권한(`GUEST`/`USER` 구분 필요 여부)
- 제공자 연결 해제의 구체적인 SDK/API, 재시도 간격·횟수 및 실패 운영 방식(연결 해제 생략은 채택하지 않음)
