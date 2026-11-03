# 구글·카카오 로그인 설정 가이드

> 작성 2026-10-28. B안(앱이 네이티브 SDK로 ID 토큰을 받고 서버가 검증)을 기준으로 한다. API는 `04-auth-api.md`.
> 콘솔 메뉴 이름은 자주 바뀐다. 이름이 다르면 같은 역할의 메뉴를 찾는다. `[확인]`은 공식 문서·실제 토큰으로 아직 확인하지 않은 항목이다.

## 0. 한눈에

| 누가 | 무엇을 | 어디에 쓰나 |
| --- | --- | --- |
| 백엔드 | 구글 **웹** 클라이언트 ID | 서버 `GOOGLE_WEB_CLIENT_ID`, 앱 `webClientId` (같은 값) |
| 백엔드 | 카카오 **네이티브 앱 키** | 서버 `KAKAO_APP_KEY`, 앱 카카오 SDK 초기화 (같은 값) |
| 백엔드 | `JWT_SECRET` 생성 | 서버만 |
| 프론트 | Android 서명 인증서 SHA-1, 카카오 키 해시 | 구글 Android 클라이언트, 카카오 Android 플랫폼 등록 |
| 프론트 | Android 패키지명 | 구글 Android 클라이언트, 카카오 Android 플랫폼 등록 |

MVP는 **Android만** 한다. iOS 설정(구글 iOS 클라이언트, 카카오 iOS 플랫폼)은 iOS를 지원할 때 추가한다.

서버는 **client secret이 필요 없다.** ID 토큰은 제공자 공개키(JWKS)로 검증하므로, 서버가 알아야 하는 건 "이 토큰이 우리 앱에 발급된 것인지" 확인할 `aud` 값뿐이다.

## 1. 공통 — 서버 실행

```bash
docker compose -f infra/docker-compose.local.yml up -d   # 레포 루트에서. 로컬 PostgreSQL·Redis
cd backend
cp .env.example .env        # 값 채우기 (.env 는 커밋하지 않는다)
./gradlew bootRun
```

- Swagger: `http://localhost:8080/swagger-ui.html`
- `.env`는 `application.yaml`의 `spring.config.import`가 읽는다. IDE 실행 설정의 환경 변수로 넣어도 된다.
- `JWT_SECRET`: `openssl rand -base64 48`로 만든다. Base64로 디코드했을 때 32바이트보다 짧으면 서버가 기동하지 않는다.
- `GOOGLE_WEB_CLIENT_ID`·`KAKAO_APP_KEY`가 비어 있어도 기동 단계에서 실패한다(설정 누락을 늦게 발견하지 않으려는 의도). 한쪽 키가 아직 없으면 임시 문자열을 넣는다 — 그 제공자 로그인만 실패한다.

## 2. 구글

### 2-1. 프로젝트와 동의 화면

1. [Google Cloud Console](https://console.cloud.google.com/)에서 팀 프로젝트를 만든다.
2. **Google 인증 플랫폼(OAuth 동의 화면)** 에서 앱 이름·지원 이메일·개발자 연락처를 입력한다.
   - 대상: **외부**
   - 범위(scope): `openid`, `email`, `profile` — 기본 범위라 따로 심사가 필요 없다.
   - 게시 상태가 **테스트**인 동안에는 **테스트 사용자**로 등록한 계정만 로그인된다. 팀원·시연 계정을 추가해 둔다. 시연 전에 "프로덕션으로 게시"로 바꿀지 결정한다.

### 2-2. OAuth 클라이언트 ID 2개 (Android MVP)

만들 것: **웹 애플리케이션 1개 + Android 1개 이상**(앱을 서명하는 키마다 1개).

공통 진입 경로: Google Cloud Console → 상단에서 팀 프로젝트 선택 → **API 및 서비스 → 사용자 인증 정보 → + 사용자 인증 정보 만들기 → OAuth 클라이언트 ID** (새 콘솔에서는 **Google 인증 플랫폼 → 클라이언트 → + 클라이언트 만들기**).

#### ① 웹 애플리케이션

| 입력란 | 값 |
| --- | --- |
| 애플리케이션 유형 | `웹 애플리케이션` |
| 이름 | `yeogirok-server` (콘솔에서 구분용. 사용자에게 보이지 않는다) |
| 승인된 JavaScript 원본 | 비워 둔다 |
| 승인된 리디렉션 URI | 비워 둔다 |

**만들기** → 팝업의 **클라이언트 ID**를 복사한다. 형태: `123456789012-xxxxxxxxxxxx.apps.googleusercontent.com`

- 서버 `.env`의 `GOOGLE_WEB_CLIENT_ID=`에 넣는다.
- 프론트에 전달한다(`GoogleSignin.configure({ webClientId })`에 같은 값).
- 팝업의 **클라이언트 보안 비밀번호(secret)는 쓰지 않는다.** 복사·공유하지 않는다.

#### ② Android

| 입력란 | 값 |
| --- | --- |
| 애플리케이션 유형 | `Android` |
| 이름 | 키 종류가 드러나게. 예: `yeogirok-android-debug`, `yeogirok-android-eas` |
| 패키지 이름 | `com.ssafya109.yeogirok` (`frontend/app.json`의 `expo.android.package`, 2026-10-28 기준) |
| SHA-1 인증서 디지털 지문 | 아래에서 구한 값. `AA:BB:CC:…` 처럼 콜론으로 구분된 40자리 16진수 |
| 고급 설정(맞춤 URI 스킴 등) | 건드리지 않는다 |

**만들기**. 결과로 나오는 클라이언트 ID는 **어디에도 넣지 않는다**(콘솔 등록 자체가 목적).

**SHA-1 구하기** — 빌드를 서명하는 키마다 다르고, Android 클라이언트 하나에 SHA-1 하나만 들어가므로 **키마다 Android 클라이언트를 따로 만든다.** 프론트에서 어떤 방식으로 빌드하는지 확인하고 해당 키만 등록한다.

| 빌드 방식 | SHA-1 확인 방법 |
| --- | --- |
| 로컬 개발 빌드 (`npx expo run:android`) | `npx expo prebuild` 후 생기는 디버그 키:<br>`keytool -list -v -keystore frontend/android/app/debug.keystore -alias androiddebugkey -storepass android -keypass android`<br>출력의 `SHA1:` 줄 |
| EAS 빌드 (`eas build -p android`) | `eas credentials -p android` → 빌드 프로필 선택 → Keystore 정보의 `SHA1 Fingerprint` (EAS 웹 대시보드 → 프로젝트 → Credentials → Android 에서도 보인다) |
| Play 스토어 배포 시 | Play Console → 앱 → 테스트 및 출시 → 설정 → **앱 무결성 → 앱 서명** 의 "앱 서명 키 인증서" SHA-1 |

- `keytool`은 JDK에 들어 있다. 명령을 찾지 못하면 `"%JAVA_HOME%\bin\keytool"`로 실행한다.
- 2026-10-28 현재 `frontend/`에 `android/` 폴더와 `eas.json`이 없다. 빌드 방식이 정해지면 그 키의 SHA-1을 프론트에 받아 등록한다.
- SHA-1이 등록되지 않았거나 다르면 앱에서 `DEVELOPER_ERROR`로 실패하고 서버까지 요청이 오지 않는다. 이 오류가 나면 먼저 **그 빌드를 서명한 키의 SHA-1**이 등록돼 있는지, 패키지 이름이 정확한지 본다.
- 등록 후 반영까지 몇 분 걸릴 수 있다.

### 2-3. nonce

서버는 nonce를 **필수**로 받는다. 앱은 로그인할 때마다 무작위 값을 만들어 SDK에 넘기고, 같은 값을 API-005에 보낸다. 없으면 C001, ID 토큰의 `nonce`와 다르면 A006이다.

- 구글: `@react-native-google-signin/google-signin`의 기본 API(`GoogleSignin.signIn()`)는 nonce를 넘길 옵션이 없다. Credential Manager 기반 라이브러리(`react-native-nitro-google-signin`의 `configure({ nonce })`)로 넘긴다.
- 카카오: `@react-native-kakao/user`의 `login({ nonce })`로 넘긴다.
- 두 방법 모두 실기기에서 토큰에 값이 그대로 들어가는 것을 확인했다(2026-11-04).

## 3. 카카오

콘솔은 왼쪽 메뉴가 **앱 설정 / 제품 설정**으로 나뉜 화면 기준이다(2026-10-28).

### 3-1. 네이티브 앱 키에 Android 정보 등록

새 콘솔에서는 플랫폼 정보를 **키에 붙여서** 등록한다. Android 앱은 네이티브 앱 키를 쓴다.

**앱 설정 → 앱 → 플랫폼 키 → 네이티브 앱 키 → `Default Native AppKey` 카드의 ⋮ → 수정**
(키를 따로 만들려면 **+ 네이티브 앱 키 추가**. 최대 5개)

| 입력란 | 값 |
| --- | --- |
| 키 이름 | 그대로 두거나 `yeogirok-android` |
| 패키지명 | `com.ssafya109.yeogirok` (`frontend/app.json`의 `expo.android.package`) |
| 스토어 URL | 패키지명을 넣으면 자동으로 채워진다. 아직 스토어에 없으면 **없음** 선택 |
| 키 해시 | 앱을 서명하는 키마다 하나씩 추가(디버그·EAS·Play 앱 서명 키). 아래에서 구한다 |

**키 해시 구하기** — 구글에 등록한 SHA-1과 같은 키에서 나온다. 형식만 다르다(SHA-1 바이트를 Base64로 바꾼 28자, `=`로 끝남).

| 빌드 방식 | 방법 |
| --- | --- |
| 로컬 개발 빌드 | `keytool -exportcert -alias androiddebugkey -keystore frontend/android/app/debug.keystore -storepass android \| openssl sha1 -binary \| openssl base64` |
| EAS 빌드 | `eas credentials -p android`의 SHA-1(`AA:BB:…`)을 변환: `echo AA:BB:… \| tr -d ':' \| xxd -r -p \| openssl base64` |
| Play 스토어 배포 | Play Console 앱 서명 키의 SHA-1을 위와 같이 변환 |

- 등록 정보가 실제 앱과 다르면 앱에서 `invalid android_key_hash or ios_bundle_id or web_site_url` 오류가 난다.
- REST API 키·JavaScript 키 카드는 건드리지 않는다. 리다이렉트 URI도 네이티브 SDK 로그인에는 필요 없다.

### 3-2. 카카오 로그인과 OpenID Connect 켜기

**제품 설정 → 카카오 로그인**

1. **사용 설정 → 상태: ON** — 꺼져 있으면 로그인 시 `KOE004` 오류.
2. **OpenID Connect → 상태: ON** — 꺼져 있으면 ID 토큰이 발급되지 않아 서버가 받을 게 없다. 카카오 로그인이 먼저 켜져 있어야 한다.

### 3-3. 동의항목

**제품 설정 → 카카오 로그인 → 동의항목 → 개인정보** 목록에서 항목 행의 **설정**

| 항목 | 동의 단계 | 동의 목적(입력 예) | 서버에서 쓰는 곳 |
| --- | --- | --- | --- |
| 닉네임 | 필수 동의 | 서비스 내 사용자 표시 | ID 토큰 `nickname` → `users.nickname` |
| 프로필 사진 | 선택 동의 | 서비스 내 프로필 이미지 표시 | `picture` → `users.profile_image_url` |
| 카카오계정(이메일) | 선택 동의 | 계정 관련 안내 | `email` → `users.email` |

- 일부 항목은 **앱 설정 → 앱 → 추가 기능 신청** 승인 뒤에만 설정할 수 있다. 이메일이 설정 불가로 나오면 건너뛴다 — 서버는 이메일 없이도 동작한다.
- 하단 **동의 화면 미리보기**로 사용자에게 보일 화면을 확인한다.
- 서버는 회원을 `sub`(카카오 회원번호)로만 식별한다.

### 3-4. 서버 `KAKAO_APP_KEY`와 `aud`

**앱 설정 → 앱 → 플랫폼 키**의 세 키 중:

| 키 | 쓰는 곳 |
| --- | --- |
| 네이티브 앱 키 | 앱 카카오 SDK 초기화, 서버 `KAKAO_APP_KEY` |
| REST API 키 | 지금은 안 쓴다(§5 서버 단독 시험 때만) |
| JavaScript 키 | 안 쓴다 |

- 키 값은 이 문서나 코드에 적지 않고 `.env`에만 둔다.
- ID 토큰 `aud`는 공식 문서상 "ID 토큰을 발급한 앱의 앱 키"다. 네이티브 SDK로 받은 토큰의 `aud`는 네이티브 앱 키다(실기기 로그인으로 확인, 2026-11-04).
- **처음 한 번 실제 토큰으로 확인한다.** 앱에서 받은 ID 토큰의 가운데 부분(payload)을 디코드해 `aud`를 본다.

  ```bash
  echo '<ID 토큰의 두 번째 . 사이 문자열>' | tr '_-' '/+' | base64 -d 2>/dev/null; echo
  ```

  나온 `aud`를 `KAKAO_APP_KEY`에 넣는다. 다르면 서버 로그에 `ID token rejected. provider=KAKAO, reason=... audience 불일치` 가 찍힌다.
- **어드민 키(앱 설정 → 앱 → 어드민 키)는 서버 밖으로 내보내지 않는다.** 지금 코드는 쓰지 않는다(탈퇴 시 연결 끊기를 구현할 때 사용, `04-auth-api.md` §6). 쓸 때는 **호출 허용 IP 주소**에 서버 IP를 등록한다.

## 4. 서버 검증 기준 (코드에 들어간 값)

`global/oauth/OAuthProvider.java`. 각 제공자의 discovery 문서(`/.well-known/openid-configuration`)에서 2026-10-28에 확인했다.

| | Google | Kakao |
| --- | --- | --- |
| `iss` | `https://accounts.google.com`, `accounts.google.com` | `https://kauth.kakao.com` |
| JWKS | `https://www.googleapis.com/oauth2/v3/certs` | `https://kauth.kakao.com/.well-known/jwks.json` |
| 서명 | RS256 | RS256 |
| `aud` | `GOOGLE_WEB_CLIENT_ID` | `KAKAO_APP_KEY` |
| `exp` | 시계 오차 60초 허용 | 같음 |
| 회원 키 | `sub` | `sub`(회원번호) |

`aud`는 쉼표 없이 목록으로 여러 개 넣을 수 있다(`application.yaml`의 `auth.oidc.*.allowed-audiences`). 웹 클라이언트를 새로 만들었거나 카카오 키 종류가 섞이면 목록에 추가한다.

## 5. 실제 토큰으로 서버만 먼저 확인하기 (선택)

앱이 준비되기 전에 서버를 시험하려면 ID 토큰을 직접 받아 Swagger로 `POST /api/v1/auth/token`을 호출한다.

- **구글**: [OAuth 2.0 Playground](https://developers.google.com/oauthplayground) → 톱니바퀴 → "Use your own OAuth credentials"에 웹 클라이언트 ID·secret을 넣는다(웹 클라이언트의 승인된 리디렉션 URI에 `https://developers.google.com/oauthplayground` 추가 필요). 범위 `openid email profile`로 인가 → 토큰 교환 응답의 `id_token`을 쓴다. `aud`가 웹 클라이언트 ID라 그대로 통과한다.
- **카카오**: 브라우저 REST 흐름(`https://kauth.kakao.com/oauth/authorize?client_id=<REST API 키>&redirect_uri=<등록한 URI>&response_type=code&scope=openid`) → 토큰 교환 응답의 `id_token`. 이 토큰의 `aud`는 **REST API 키**이므로 시험하는 동안만 `allowed-audiences`에 REST API 키를 추가한다 `[확인]`.

## 6. 체크리스트

- [ ] 구글: 동의 화면, 테스트 사용자, 웹·Android 클라이언트 ID
- [ ] 구글: 디버그·EAS 키 SHA-1 등록(프론트에서 받기)
- [ ] 카카오: 네이티브 앱 키에 패키지명·키 해시 등록
- [ ] 카카오: 카카오 로그인 ON, **OpenID Connect ON**, 동의항목
- [ ] 서버 `.env`: `JWT_SECRET`, `GOOGLE_WEB_CLIENT_ID`, `KAKAO_APP_KEY`
- [ ] 프론트에 전달: 웹 클라이언트 ID, 카카오 네이티브 앱 키
- [ ] 카카오 실제 토큰의 `aud` 확인 후 `KAKAO_APP_KEY` 확정
