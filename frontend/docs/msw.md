# MSW 사용법

MSW는 프론트엔드의 HTTP 요청을 가로채 목 응답을 반환한다. 백엔드 없이 요청·응답 처리와 오류 상태를 개발할 수 있다. 실제 서버 구현의 정상 동작은 서버 연결 후 별도로 확인한다.

## 실행

1. `pnpm install`을 실행한다.
2. `.env.example`의 `EXPO_PUBLIC_API_URL=https://api.yeogirok.test`를 `.env.local`에 추가한다. 기존 카카오 등 환경 변수는 유지한다.
3. 개발 빌드에서 `pnpm start`를 실행한다. 웹 확인은 `pnpm web`을 사용한다.

API URL에는 호스트만 넣는다. 도메인 API 요청 경로의 기준은 `/api/v1`이며, HTTP 클라이언트를 구현할 때 이 경로를 붙인다. 배포 환경에는 실제 백엔드 HTTPS 호스트를 설정한다. 환경 변수가 없으면 앱 시작 시 오류로 처리한다.

개발에서는 별도 켜기 설정 없이 MSW가 항상 시작된다. MSW 준비에 실패하면 라우터도 시작하지 않는다. 배포에서는 `__DEV__` 분기로 목 모듈과 폴리필을 불러오지 않는다.

| 파일 | 역할 |
| --- | --- |
| `index.ts` | 환경 변수 확인 → 개발 환경 MSW 준비 → Expo Router 시작 |
| `api/mocks/start.ts` | 네이티브 폴리필을 먼저 적용한 뒤 서버 시작 |
| `api/mocks/start.web.ts` | 웹 Service Worker 준비 완료 후 반환 |
| `api/mocks/native.ts` | Android·iOS용 `msw/native` 서버 |
| `api/mocks/server.ts` | 향후 jest에서 사용할 `msw/node` 서버 |
| `api/mocks/handlers/index.ts` | 도메인 핸들러 등록 위치 |
| `api/mocks/on-unhandled-request.ts` | 미등록 서비스 API 차단, 그 외 요청 통과 |
| `public/mockServiceWorker.js` | MSW CLI가 생성한 웹 Worker |

MSW는 **2.3.5로 고정**한다. 설치 시 확인한 2.15.0의 루트 모듈은 `BroadcastChannel`을 사용하지만 현재 React Native에는 해당 전역이 없다. 2.3.5는 이를 요구하지 않는다. 버전 변경 시 네이티브 검증과 `pnpm exec msw init public --save`를 함께 실행한다. Worker는 직접 편집하지 않는다.

`pnpm-workspace.yaml`의 `allowBuilds.msw: false`는 설치 후 Worker를 자동 복사하는 스크립트를 실행하지 않는 설정이다. Worker 갱신은 위 명령으로 명시적으로 수행한다.

## 도메인 핸들러 추가

현재 도메인 API가 구현되지 않아 `handlers`는 빈 배열이다. 서비스 API를 호출하기 전에 해당 핸들러를 추가한다.

1. Notion·Swagger에서 메서드, 경로, 요청·응답 필드와 오류 코드를 확인한다.
2. `api/mocks/fixtures/<domain>.ts`에 명세와 같은 응답 예시를 작성한다. 성공은 `{ success: true, data }`, 실패는 `{ success: false, code, message }` 봉투를 포함한다.
3. `api/mocks/handlers/<domain>.ts`에 `msw`의 `http`와 `HttpResponse`로 핸들러를 작성한다. 경로는 `*/api/v1/` 아래의 명세 경로로 지정한다.
4. `handlers/index.ts`의 배열에 도메인 핸들러를 펼쳐 등록한다.
5. 앱을 다시 로드하고 기존 HTTP 요청 흐름으로 확인한다. 화면·도메인 API 함수에는 목 분기를 넣지 않는다.

미등록 `/api/v1` 및 `/api/v1/…` 요청은 실제 서버로 보내지 않고 실패시킨다. 외부 SDK·이미지 등 다른 경로의 요청은 통과시킨다. 목에는 실제 개인 정보나 인증 토큰을 넣지 않는다.

## 설정 검증

```bash
pnpm typecheck
pnpm lint
pnpm msw:check
```

`msw:check`는 기존 TypeScript 컴파일러와 Node의 기본 assert만 사용한다. 임시 산출물은 git에서 제외된 `.expo/msw-check/`에 생성한다. 네이티브·Node 서버 각각에 대해 정상 JSON 응답, 오류 응답 덮어쓰기, 핸들러 초기화, 미등록 서비스 API의 네트워크 차단, 외부 요청 통과를 검사한다. 점검용 핸들러는 이 명령에서만 추가되며 앱에는 등록되지 않는다.

이 검사는 실제 기기에서의 Hermes·네트워크 동작을 대신하지 않는다. 개발 빌드에서 화면과 도메인 API를 연결할 때 기기에서도 확인한다.

## jest 연결 (도구 설치 후)

jest-expo는 아직 설치되지 않았으므로 `pnpm test`는 제공하지 않는다. 향후 jest 설정 시 `api/mocks/server.ts`를 사용한다.

```ts
import { server } from "./api/mocks/server";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

테스트별 오류 시나리오는 `server.use(...)`로 덮어쓴다. 테스트에서는 외부 요청도 미처리 요청으로 실패시킨다.
