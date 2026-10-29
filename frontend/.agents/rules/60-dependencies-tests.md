---
paths:
  - "package.json"
  - "pnpm-lock.yaml"
  - "eslint.config.js"
  - "eslint.config.mjs"
  - "eslint.config.ts"
  - "tsconfig.json"
  - "tsconfig.*.json"
  - "app.json"
  - "babel.config.js"
  - "metro.config.js"
  - "jest.config.js"
  - "jest.setup.*"
  - "**/*.test.ts"
  - "**/*.test.tsx"
  - "**/*.spec.ts"
  - "**/*.spec.tsx"
  - "tests/**"
  - "api/mocks/**"
  - ".maestro/**"
---

# 의존성, 설정 및 테스트

## 의존성

- 패키지 작업은 `pnpm`으로만 한다.
- 네이티브 모듈이 포함되거나 Expo SDK 버전에 민감한 패키지는 `pnpm add`가 아니라 `pnpm expo install`로 설치해 호환 버전을 맞춘다. 네이티브 모듈의 선택과 경계는 규칙 85를 따른다.
- lockfile(`pnpm-lock.yaml`)을 직접 수정하지 않는다.
- 이미 설치된 라이브러리로 해결할 수 있는 문제에 같은 목적의 새 라이브러리를 추가하지 않는다.
- 새로운 패키지 설치, 기존 패키지 제거, 주요 버전 변경, Expo SDK 업그레이드 전에는 확인을 요청한다. 예외로, MSW(`msw@2`, 앱용 폴리필 `react-native-url-polyfill`·`fast-text-encoding`, 개발 의존성)는 사용자가 도입을 결정했다(2026-10-27). 처음 설치할 때 설치한다는 사실만 보고한다.
- 오류를 없애려고 ESLint나 TypeScript 규칙을 비활성화하거나 완화하지 않는다.

## 테스트 범위

테스트는 세 층으로 나눈다. 비용 대비 효과 순서로 쓴다.

| 층 | 도구 | 대상 | 언제 쓰나 |
| --- | --- | --- | --- |
| 단위 | jest-expo | `features/<domain>/model.ts`의 순수 함수·계약 변환, `lib/` 유틸 | 새 `model.ts`·유틸을 만들 때 함께 쓴다 |
| API | jest-expo + MSW(`msw/node`) | 도메인 API 함수·Query 옵션의 계약 처리(봉투, 오류 code, 커서, 작업 상태 전이) | 새 도메인 API를 만들 때 대표 시나리오 1개 이상 |
| E2E | Maestro | P0 시연 흐름(`docs/frontend-spec.md` §0) | 해당 흐름이 완성될 때 flow 1개 |

- 화면 통합 테스트(`*.test.tsx`)는 요청이 있을 때만 새로 만든다(사용자 결정 2026-09-08). 화면 동작은 Maestro E2E로 확인한다.
- `model.ts` 테스트는 Notion·Swagger 명세에 맞춘 fixture로 쓴다. 다음을 포함한다.
  - `null` 필드
  - 모르는 열거형(`UNKNOWN` 흡수)
  - 날짜·시각 형식 위반(`2026-07-27 15:30:00+09` 외 형식 → `ContractMismatchError`)
  - 위경도 범위
  - 커서 경계(다음 커서 `null`)
- 기존 `*.test.tsx`는 그대로 둔다. 건드린 화면의 동작이 바뀌면 깨지지 않게 고친다. 통과를 목적으로 테스트를 삭제하거나 검증 수준을 낮추지 않는다.
- 테스트 도구는 `jest-expo` + React Native Testing Library + MSW, E2E는 Maestro다. 승인 없이 다른 테스트 프레임워크를 들이지 않는다.
- 테스트는 내부 구현 세부 사항보다 겉으로 확인할 수 있는 동작을 검증한다.
- 검증 명령을 실행하지 못했으면 실행한 것처럼 보고하지 않는다.

## MSW (API 목)

- 핸들러·응답 예시 위치, 봉투 형식, 켜는 방법은 규칙 90 "목 서버(MSW)와 테스트"와 `docs/api-guide.md` §9를 따른다.
- jest는 `jest.setup`에서 `setupServer`를 켠다. 순서는 `beforeAll(listen)` → `afterEach(resetHandlers)` → `afterAll(close)`다. 테스트 안의 오류 시나리오는 `server.use(...)`로 덮어쓴다. 처리되지 않은 요청은 테스트 실패로 본다(`onUnhandledRequest: "error"`).
- 앱 실행 중 목(`msw/native`)은 개발 환경에서 항상 켠다. 배포 환경에서는 목 모듈을 불러오거나 시작하지 않도록 `__DEV__` 분기 안에서 동적으로 import한다.

## E2E (Maestro)

- 위치
  - flow: `.maestro/flows/<NN>-<흐름>.yaml`. 예: `10-course-to-room.yaml`. 번호는 시연 순서다.
  - 여러 flow가 쓰는 단계: `.maestro/subflows/`. 예: `login.yaml`
  - 설명: `.maestro/README.md`
- 실행: `pnpm e2e`(= `maestro test .maestro/flows`).
  - 앱 id는 flow에 직접 쓰지 않는다. 환경 변수 `MAESTRO_APP_ID`로 넘기고 flow에서 `${MAESTRO_APP_ID}`로 받는다(Maestro는 `MAESTRO_` 접두 변수를 자동으로 읽는다).
  - Maestro CLI는 npm 패키지가 아니므로 각자 설치한다. CI는 두지 않는다.
- 빌드: `__DEV__ === true`인 개발 빌드에서 실행해 전체 API를 MSW로 처리한다(규칙 85·90). 서버 없이 실행하는 E2E에 배포 빌드를 쓰지 않는다.
- 요소 찾기
  - 요소는 `testID`로 찾는다. Maestro에서는 `id:` 선택자다. 화면 문구는 바뀌기 쉬우므로 텍스트 매칭은 `assertVisible` 확인에만 쓴다.
  - `testID` 이름은 `<화면>.<요소>` kebab-case로 짓는다. 예: `room-list.create-button`, `footprint.submit`.
  - 시연 흐름에 쓰이는 버튼·입력·탭에는 `testID`를 붙인다. `testID`가 접근성 라벨을 대신하지 않으므로 `accessibilityLabel`도 따로 둔다(규칙 40).
- 권한과 위치
  - 권한 상태는 `launchApp`의 `permissions`로 정한다. 허용 흐름과 거부 흐름(COM-03·04 안내)을 각각 만든다.
  - 현재 위치가 필요한 흐름은 `setLocation`으로 고정 좌표(공개 장소)를 넣는다.
- AR 카메라 화면 자체는 자동화하지 않는다. AR 미지원·카메라 거부 폴백 경로만 자동화한다.
- flow가 깨지면 앱 동작이 바뀐 것인지 flow가 낡은 것인지 먼저 구분한다. 통과시키려고 확인(`assert*`)을 지우지 않는다.
