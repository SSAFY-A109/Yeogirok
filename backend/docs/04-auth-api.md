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
{ "refreshToken": "eyJ...", "pushToken": "fcm-device-token" }
```

`pushToken`(선택): 이 기기의 FCM 토큰. 주면 로그아웃과 함께 그 회원의 그 토큰만 지운다(API-096 과 같다, 다른 계정의 토큰은 지우지 않는다). 앱이 API-096 을 빠뜨려도 로그아웃한 기기에 채팅 미리보기가 가지 않게 한다(프론트와 합의). 생략·null 이면 지우지 않는다. 문자열이 아니면 C002, 빈 값·공백만·4,096자 초과면 C003 이고 그때는 세션도 폐기하지 않는다. 토큰 삭제가 실패해도 세션은 폐기하고 오류(C014)를 돌려주며, 다시 부르면 둘 다 성공한다.
응답 `200` — `{ "success": true, "data": null }`. 현재 기기 세션의 리프레시 토큰만 폐기하며 다른 기기 세션은 유지한다. 이미 폐기된 토큰이어도 성공으로 응답한다(멱등). 앱은 비공개 데이터 캐시와 토큰을 즉시 지운다. 초기 구현에서는 로그아웃한 세션의 기존 액세스 토큰을 서버에서 즉시 폐기하지 않고 만료까지 유효하게 둔다. 이전 `data: { loggedOut: true }` 응답과 필드 행은 Notion의 폐기 명세에 보존한다.

실패: 공통 C004·C005·C006(액세스 토큰).

### API-008 `GET /users/me`

Notion 상세(2026-11-04 확인)를 따른다. 요청 — `Authorization: Bearer <액세스 토큰>`, 본문 없음.

```json
{ "id": 1, "nickname": "여행자", "profileImageUrl": null,
  "activityCounts": { "footprints": 0, "tips": 0 }, "loginProviders": ["google"] }
```

- `activityCounts`: 발자취·팁 도메인이 생기기 전까지 0이다.
- `loginProviders`: 회원 하나에 제공자 하나라 원소가 하나다.
- 실패: A001·A002·A003. 토큰의 회원이 없거나 탈퇴했으면 A003으로 응답한다(명세의 실패 응답이 A001~A003뿐이다).

### API-009 `DELETE /users/me`

Notion 상세(2026-11-04 확인)를 따른다.

요청 — `Authorization: Bearer <액세스 토큰>`. 본문은 구글 회원만 보낸다.

```json
{ "googleAuthCode": "4/0AVG..." }
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `googleAuthCode` | string | 구글 회원만 | 탈퇴 직전 재로그인 때 구글 SDK가 준 `serverAuthCode`(`offlineAccess: true`). 서버가 토큰으로 바꿔 구글 연결 해제에 쓴다 |

응답 `200` — `{ "withdrawn": true, "dataCleanupStatus": "QUEUED" }`. 후속 데이터 정리는 서버 내부 작업이라 앱이 작업 상태를 조회하지 않는다.

**최근 재인증(A011, 2026-11-04 결정).** 소셜 로그인(API-005)으로 받은 액세스 토큰에는 로그인 시각 `auth_time`(epoch 초)이 들어 있다. 재발급(API-006) 토큰에는 없다. 탈퇴는 `auth_time`이 있고 **5분**(`auth.withdrawal.reauth-max-age`, 제안값) 안인 토큰으로만 할 수 있다. 앱 흐름:

1. 사용자가 탈퇴를 누르면 앱이 제공자 SDK로 다시 로그인한다. 구글이면 `offlineAccess: true`로 ID 토큰과 `serverAuthCode`를 같이 받는다.
2. API-005로 새 서비스 토큰을 받는다.
3. 새 액세스 토큰으로 API-009를 부른다. 구글이면 본문에 `googleAuthCode`를 넣는다.

처리 순서(2026-11-04 결정):

1. 최근 재인증을 확인한다(A011).
2. 구글이면 `googleAuthCode`를 구글 토큰 엔드포인트에서 토큰으로 바꾸고, 함께 온 ID 토큰의 `sub`가 이 회원과 같은지 확인한다(A029). 외부 호출이라 트랜잭션 밖에서 한다. 구글 토큰은 저장하지 않는다.
3. 한 트랜잭션에서 회원을 `WITHDRAWN`으로 바꾸고 `withdrawn_at`을 기록한다. 이메일·프로필 사진은 지우고 닉네임은 "탈퇴한 회원"으로 바꾼다. 그 회원의 리프레시 토큰을 모든 기기에서 폐기한다.
4. `provider_subject`를 `withdrawn:{회원 id}`로 바꾼다. 같은 제공자 계정으로 다시 로그인하면 **새 회원으로 가입**한다(이전 데이터와 이어지지 않는다).
5. 커밋 뒤 제공자 연결을 해제한다. 실패해도 탈퇴는 성공한다.
   - 카카오: 서버가 어드민 키로 `POST /v1/user/unlink`(`target_id_type=user_id`, `target_id=sub`). 실패하면 Redis(`auth:unlink-task:{회원 id}`, 목록 `auth:unlink-tasks`)에 원래 sub와 함께 남기고 `auth.unlink.retry-interval`마다 `max-attempts`까지 다시 시도한다(1시간 / 24회, 약 하루).
   - 구글: 2에서 받은 리프레시 토큰(없으면 액세스 토큰)으로 `POST https://oauth2.googleapis.com/revoke`. 토큰을 저장하지 않으므로 실패하면 로그만 남기고 다시 시도하지 않는다.
6. 이미 발급된 액세스 토큰은 만료까지 서명이 유효하다. 커밋 뒤 Redis에 `auth:withdrawn:{회원 id}`를 액세스 토큰 수명만큼 남기고, 서비스 JWT 검증 단계에서 이 키가 있으면 A003으로 막는다. 그래서 모든 API가 따로 탈퇴 여부를 확인하지 않아도 된다.

실패:

| HTTP | code | 조건 |
| --- | --- | --- |
| 400 | C001 | 구글 회원인데 `googleAuthCode`가 없음 |
| 401 | A001·A002·A003 | 액세스 토큰 누락·만료·무효. 회원이 없거나 이미 탈퇴함(A003) |
| 401 | A011 | 최근 재인증 필요: 재발급 토큰이거나 로그인 후 5분이 지남 |
| 401 | A029 | 구글 인증 코드를 교환할 수 없음(잘못됨·만료·이미 씀) 또는 다른 구글 계정의 코드 |

A011·A029·C001이면 탈퇴하지 않는다(계정·세션 그대로).

구글 `serverAuthCode`를 교환할 때 `redirect_uri`를 빈 문자열로 보낸다(Android 앱의 서버 인증 코드 방식). 실기기에서 코드 교환과 연결 해제까지 확인했다(2026-11-04).

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

### 확정 정책(2026-10-28, 명세 기준. 탈퇴는 2026-11-04 구현 — §3 API-009)

- 다기기 동시 로그인을 허용한다. 리프레시 토큰은 기기별 세션 단위로 관리하며, 새 기기 로그인이 기존 기기 세션을 종료하지 않는다. 토큰 회전은 해당 세션 안에서 수행한다. 저장은 Redis다(Notion ERD › Redis §2: `auth:refresh`·`auth:session`·`auth:user-sessions`).
- 로그아웃은 현재 기기 세션의 리프레시 토큰만 즉시 폐기한다. 기존 액세스 토큰은 만료까지 유효하며 앱에서 즉시 삭제한다.
- 탈퇴(API-009)는 서비스 계정 비활성화와 모든 기기 세션 폐기를 먼저 처리하고, 제공자 연결 해제를 수행한다. 연결 해제 실패가 서비스 탈퇴를 막지 않으며 서버에서 실패 작업을 보관해 재시도한다. 카카오는 서버가 어드민 키로, 구글은 서버가 탈퇴 요청의 `googleAuthCode`로 받은 토큰으로 해제한다(2026-11-04).
- 탈퇴 전 최근 재인증(A011)을 강제한다. 소셜 로그인 후 5분(제안값) 안의 액세스 토큰만 허용한다(2026-11-04).
- 탈퇴한 회원이 같은 제공자 계정으로 다시 로그인하면 새 회원으로 가입한다(2026-11-04).
- 액세스·리프레시 토큰 수명 및 로그인 유지 기간 정책은 이번 결정에서 변경하거나 확정하지 않는다.

### 미정 사항

- 액세스·리프레시 TTL(제안 30분 / 14일; 기존 액세스 예시 3600초 보존)
- 비회원 범위(DEC-19), 온보딩 완료 전 권한(`GUEST`/`USER` 구분 필요 여부)
- 연결 해제 재시도 24회를 넘긴 작업의 운영 방식(Redis에 남겨 두며 지금은 수동 확인)
- 탈퇴 전 최근 재인증(A011)의 기준 시간(제안 5분)
- 탈퇴 시 바꾸는 `provider_subject` 형식(`withdrawn:{회원 id}`)의 ERD 반영
