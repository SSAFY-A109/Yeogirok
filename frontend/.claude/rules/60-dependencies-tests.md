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

| 층   | 도구                        | 대상                                                                          | 언제 쓰나                                      |
| ---- | --------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------- |
| 단위 | jest-expo                   | `features/<domain>/model.ts`의 순수 함수·계약 변환, `lib/` 유틸               | 새 `model.ts`·유틸을 만들 때 함께 쓴다         |
| API  | jest-expo + MSW(`msw/node`) | 도메인 API 함수·Query 옵션의 계약 처리(봉투, 오류 code, 커서, 작업 상태 전이) | 새 도메인 API를 만들 때 대표 시나리오 1개 이상 |
| E2E  | Maestro                     | P0 시연 흐름(`docs/frontend-spec.md` §0)                                      | 해당 흐름이 완성될 때 flow 1개                 |

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

# Maestro E2E 테스트 규칙

> 대상: React Native Android/iOS 앱. AI가 Maestro Flow를 생성·수정할 때 이 규칙을 적용한다.
> **핵심:** 내부 구현이 아니라 **사용자가 수행하는 행동과 화면에서 확인하는 결과**를 검증한다.

## 1. 테스트 범위와 시나리오

- E2E가 꼭 필요한 **핵심 사용자 흐름(P0)**부터 작성한다. P1(주요 보조 기능), P2(UI 선호 기능)는 위험도에 따라 추가한다.
- **하나의 Flow는 하나의 독립적인 사용자 시나리오**를 검증한다. 단, 결제처럼 하나로 연결된 사용자 여정은 여러 단계를 포함할 수 있다.
- 정상 흐름뿐 아니라 중요한 **입력 오류·권한 오류·서버 오류**도 위험도에 따라 검증한다.
- 유틸 함수, 내부 상태, 단순 렌더링 등은 Unit/Component 테스트를 우선 고려한다. 기존 E2E와 중복되면 새 Flow를 만들지 않는다.
- 파일명은 `login-invalid-password.yaml`처럼 영문 kebab-case, `name`은 **행동 + 기대 결과**가 드러나게 작성한다.

## 2. 선택자와 사용자 동작

- 우선 **의미 있고 유일한 화면 텍스트/접근성 레이블(`text`)** 또는 **안정적인 `testID`(`id`)**를 사용한다. 동적 텍스트·아이콘 버튼에서는 `id`가 더 적합할 수 있다.
- 반복 항목은 의미 있는 컨테이너 ID와 관계형 선택자로 구분한다. **`index`, 좌표(`point`), 과도한 정규식은 최후의 수단**으로만 사용한다.
- 실제 UI 조작에는 `tapOn`, `inputText`, `scrollUntilVisible`, `back` 등 Maestro 명령을 사용한다. JavaScript로 사용자 조작을 우회하지 않는다.
- 요소를 식별하기 어렵다면 앱의 `accessibilityLabel`/`testID` 개선을 검토하되, 테스트만 통과시키려고 화면 문구나 기능을 왜곡하지 않는다.

## 3. 대기와 검증

- **임의의 고정 sleep 금지.** 기본 자동 대기와 `assertVisible`/`assertNotVisible`을 우선 사용한다.
- 오래 걸리는 작업에만 `extendedWaitUntil`, 목록 탐색에는 `scrollUntilVisible`, 애니메이션 문제에는 필요한 경우 `waitForAnimationToEnd`를 사용한다.
- React state, Redux/Zustand, 함수 호출, 뷰 구조 대신 **화면 전환·안내 메시지·저장된 결과 등 관찰 가능한 변화**를 검증한다.
- `optional`, 반복 retry, 의미 없는 assertion으로 실패를 숨기지 않는다. 처음 실패하고 재시도에 성공했다면 원인을 조사한다.

## 4. 독립성·데이터·인증

- 모든 **최상위 Flow는 단독·순서 무관하게 실행**할 수 있어야 한다. 이전 Flow의 로그인 상태·데이터·실행 결과에 의존하지 않는다.
- 초기 앱 상태는 목적에 맞게 `launchApp`의 `clearState` 등을 명시한다. **서버 데이터까지 자동 초기화된다고 가정하지 않는다.**
- 검증 대상이 아닌 사전 작업은 기존 테스트 데이터 준비 스크립트·API(`runScript`)를 우선 활용하고, 필요할 때만 UI로 준비한다. 데이터 충돌을 방지하고 생성 데이터는 정리한다.
- 로그인 자체를 검증할 때만 실제 UI 로그인 과정을 테스트한다. 다른 Flow는 프로젝트에 안전한 인증 준비 방식이 있으면 사용하고, 없으면 기존 로그인 `runFlow`를 재사용한다.
- **핵심 E2E는 실제 Backend 연결을 우선**한다. 장애/특수 상황만 제어된 테스트 서버에서 Mock한다. 계정·토큰 등 비밀값은 환경 변수/CI Secret으로 관리한다.

## 5. 재사용·플랫폼·실행

- 작성 전 기존 `.maestro/`의 Flow, `config.yaml`, Subflow, Script, 이름·선택자·태그 규칙을 확인하고 **기존 패턴을 우선 재사용**한다.
- 공통 로그인·데이터 준비처럼 **의미 있는 반복**에만 `runFlow`/`runScript`를 사용한다. 한 줄짜리 동작을 불필요하게 추상화하지 않는다.
- Android/iOS 간 키보드·권한·뒤로 가기 등 차이를 고려하고, 플랫폼별 분기는 실제로 필요할 때만 작성한다.
- 테스트 추가·수정 후 **해당 Flow를 단독 실행**하고 관련 Smoke 테스트도 확인한다. 실패 시 로그·스크린샷·화면 상태를 근거로 원인을 분석한다. 실행하지 못했으면 **미실행 사실과 이유**를 명시한다.
- 테스트 통과를 위한 production 우회 코드, 무분별한 재시도, 테스트 의존 관계를 추가하지 않는다.

## 6. 최소 Flow 예시

```yaml
appId: ${APP_ID}
name: 올바른 계정으로 로그인하면 홈 화면이 표시된다
tags:
  - smoke
---
- launchApp:
    clearState: true
- tapOn:
    id: email-input
- inputText: ${TEST_EMAIL}
- tapOn:
    id: password-input
- inputText: ${TEST_PASSWORD}
- tapOn: "로그인"
- assertVisible: "홈"
```

> 위 `APP_ID`, `TEST_EMAIL`, `TEST_PASSWORD`, `id`, 텍스트는 **실제 프로젝트에서 확인한 값**으로 대체한다. 없는 API·식별자·딥링크·Maestro 명령을 추측해서 만들지 않는다.

## AI 작업 순서

**기존 테스트 확인 → 사용자 시나리오·성공 조건 결정 → 데이터·인증 준비 → Flow 작성 → 사용자 결과 검증 → 단독 실행 → 실패/미실행 근거 보고.**
