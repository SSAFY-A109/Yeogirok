# 로그인 화면

- 작업: S15P31A109-82. 미인증 첫 진입은 `/(auth)/login`(`/login`), 로그인 성공 후는 메인 `/`다. 루트 Stack에서 인증 상태로 접근을 제한한다. 로그인 화면에는 기본 헤더와 하단 탭을 표시하지 않는다.
- Figma: [W01 로그인](https://www.figma.com/design/gzYo8vG6h2LrKGOa22UCFP/Untitled?node-id=20-3783). 2026-11-03 디자인 컨텍스트·스크린샷 대조. Pencil 노드 없음(미대조).
- 기존 로고와 공용 소셜 버튼을 재사용한다. `assets/images/auth/login-map.png`는 Figma `20:3789`의 정적 지도 일러스트 내보내기(350×232)이며, 화면 전체 캡처가 아니다. 지도·경로·발자취를 원래 위치로 보존한다.

## 이번 구현 범위

후속 사용자 요청(2026-11-03)으로 개발 MSW 로그인과 배포 네이티브 SDK 연결까지 구현 범위를 확대했다. 브라우저·실기기 실행은 하지 않고 정적 검사와 Node 계약 검사로 검증한다. 화면은 스크롤 없이 안전 영역 안의 전체 높이를 차지하고, 남은 높이에 맞춰 로고와 지도 크기를 줄인다.

개발(`__DEV__`)에서는 fixture 자격 증명으로 MSW API-005를 호출한다. 배포에서는 Google Sign-In 또는 Kakao User SDK가 발급한 ID 토큰을 같은 API 함수로 교환한다. 성공한 서비스 토큰 쌍을 SecureStore에 저장한 뒤 인증 상태를 갱신하고 메인 `/`로 이동한다. 로그인 화면은 내비게이션 이력에서도 제외된다. 요청 중에는 버튼을 잠그고 취소·실패 시 이동하지 않는다.

TanStack Query가 로그인 mutation과 저장소 세션 조회를 담당한다. Query 캐시에는 인증 여부만 두며, mutation은 토큰을 반환하지 않는다. SDK 모듈은 배포 인증 시점에만 불러오므로 개발 목 로그인은 네이티브 SDK 초기화 없이 사용할 수 있다. `/`의 메인은 기존 개발 홈을 유지하며 새 메인 디자인·온보딩은 포함하지 않는다.

## 배포 설정

승인 후 Expo 설치 절차로 Google Sign-In 17.0.0, Kakao Core/User 2.4.11, TanStack Query 5.105.3을 추가했다. SDK API·Expo plugin 옵션은 설치된 공식 배포 패키지의 소스·타입으로 확인했다.

아래 공개 설정값의 빈 항목을 `.env.example`과 로컬 `.env.local`에 준비했다. 클라이언트 비밀키는 넣지 않는다.

| 환경 변수 | 값 |
| --- | --- |
| `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` | 서버가 ID 토큰의 aud를 검증할 Google Web OAuth 클라이언트 ID |
| `EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID` | iOS OAuth 클라이언트 ID. 역순 URL scheme은 Expo 설정에서 생성 |
| `EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY` | Kakao 앱의 Native App Key |
| `EXPO_PUBLIC_API_URL` | 실제 배포 백엔드 HTTPS 호스트 |

- Google: Android 패키지 `com.ssafya109.yeogirok`와 배포 서명 SHA-1로 Android OAuth 클라이언트를 등록하고, iOS bundle ID도 같은 프로젝트에 등록한다. Firebase 설정 파일을 요구하는 구성은 사용하지 않는다.
- Kakao: Android 패키지·키 해시, iOS bundle ID를 등록하고 카카오 로그인과 OpenID Connect를 활성화해 `idToken`이 발급되도록 한다. 서버에도 해당 OIDC 앱의 aud 설정이 필요하다.
- `app.config.ts`는 키가 있을 때 Kakao 인증 callback과 Google iOS URL scheme plugin을 추가한다. 키를 넣거나 바꾼 뒤에는 **네이티브 개발 빌드·배포 빌드 재생성**이 필요하다. 이미 생성된 Android/iOS 프로젝트는 prebuild 동기화 후 빌드한다. 기존 로컬 데이터 보호를 위해 무조건적인 clean prebuild는 하지 않는다.
- 키 누락은 앱 초기화 전체를 중단하지 않고 로그인 시 안내한다. SDK 취소·ID 토큰 누락·서비스 API 오류·저장 실패는 메인 이동으로 처리하지 않는다. 배포 웹 로그인은 지원하지 않으며 앱 이용을 안내한다.
- 제공자 키는 아직 입력하지 않았다. 실제 SDK 로그인·네이티브 빌드·서버 검증 성공은 미확인이다.

## API 계약

원천: [API-005](https://app.notion.com/p/402debe071c381e58b0add45607da132), [API-006](https://app.notion.com/p/402debe071c381f7ac0defad7a91b6fd). 연결 도구는 404를 반환했으며, 사용자가 2026-11-03 제공한 해당 페이지 본문을 기준으로 구현했다. 원천은 설계 초안·서버 미구현 상태다.

- 현재 계약은 **네이티브 SDK의 OIDC ID 토큰 교환**이다. 저장소의 이전 시스템 브라우저 인증/API-001·002 안내와 다르며, 이번 작업에서는 사용자가 제공한 현재 계약을 따른다. SDK 도입 시 기존 인증 지침도 함께 갱신해야 한다.
- `features/auth/api/auth.api.ts`의 `exchangeSocialToken`은 API-005를, `refreshServiceTokens`는 API-006을 호출한다. 공통 클라이언트가 봉투를 해제하고 인증 헤더를 제외한다. `nonce`는 선택이며 서버가 반환하는 만료 시간과 `isNewUser`를 검증한다.
- API 함수는 결과만 반환한다. `performSocialLogin`이 서비스 토큰을 저장하고 Query mutation 성공 후 세션을 다시 읽는다. 기존 공통 클라이언트의 자동 갱신은 회전한 토큰 쌍 저장, 실패 시 삭제·인증 해제와 Query 캐시 정리를 담당한다.
- 토큰 수명·로그인 유지 기간·온보딩 분기는 TBD다. 검토 수치를 서비스 정책으로 사용하지 않는다.

## 개발 목과 검사

`api/mocks/fixtures/auth.ts`의 제공자별 가짜 ID 토큰으로 발급 요청을 할 수 있다. `nonce` 생략은 허용하고, 전달하면 fixture 값과 대조한다. 잘못된 ID 토큰은 A005, nonce 불일치는 A006이다.

목은 로그인마다 독립된 세션을 만들고, 갱신 시 이전 리프레시 토큰을 즉시 무효화한다(A010). 만료·폐기 시나리오는 `failedRefreshTokensFixture`의 전용 값으로 A008·A009를 재현하고, 나머지 무효 토큰은 A007이다. **60초·120초는 응답 검사용 가상 수명**이며, 실제 시간 경과나 제공자 JWT 서명을 검증하는 목이 아니다. 오류 시나리오를 추가할 때는 MSW `server.use`로 응답을 덮어쓴다.

```sh
pnpm auth:check
pnpm api:check
pnpm native:check
pnpm msw:check
pnpm typecheck
pnpm lint
pnpm tokens:check
```

`auth:check`는 개발 로그인·토큰 저장, SDK 어댑터의 ID 토큰 전달·키 누락·취소, Expo plugin 설정, 요청·응답, nonce, 오류 코드, 세션 독립성, 토큰 회전과 자동 갱신을 검사한다. SDK는 Node에서 대역으로 검증하며 실제 제공자 인증·네이티브 빌드·서버 검증을 대신하지 않는다.

2026-11-03 앱 로그인 오류 보완: Node 검사에서만 fetch 어댑터를 강제해 앱의 기본 XHR 경로를 놓쳤다. RN Response의 스트림 `body` 부재 때문에 MSW XHR 응답 본문이 사라지는 것을 재현했고, 공통 클라이언트 자체를 fetch 어댑터로 수정했다. 검사별 어댑터 강제 설정을 제거하고 `native:check`로 회귀를 방지한다. JS 변경이므로 이 수정에 네이티브 재빌드는 필요하지 않다.
