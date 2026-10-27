# 프로젝트: 여기록 — 다른 사람의 발자취를 따라 여행하고, 나의 발자취는 또 다른 사람의 여행이 되는 여행 코스 공유 서비스 (React Native 클라이언트)

## 프로젝트 기본 정보

- **여기록**의 React Native 클라이언트다. 서비스는 다음 흐름이 하나로 이어진다.
  - 다른 사람의 공개 코스에서 원하는 장소만 가져와 여행방에서 일행과 일정을 만든다(AI 일정 초안).
  - 여행 중에는 지도 탭에 발자취(본인만 보는 방명록 + 방문 통계)를 남기고, AR로 현장 팁을 본다.
  - 여행 뒤에는 사진·영수증으로 AI 기록 초안을 만들어 일행과 여행기를 완성하고, 다시 코스로 공개한다.
- 사용자와 플랫폼
  - 사용자는 소셜 로그인(구글·카카오) 회원이다. 비회원 범위는 DEC-19에서 정한다.
  - 대표 상황은 친구들과 떠나는 1박 2일이다.
  - 플랫폼은 Android·iOS(390pt 기준 디자인)이고, 배포 방식은 `TBD`다.
- 기능 범위·화면·우선순위(P0~P3)는 `docs/frontend-spec.md`가 기준이다. 원천은 팀 Notion "기능 명세서 v2"다.
- 백엔드의 위치·기술은 `TBD`다. 계약의 원천은 팀 Notion "API 명세"이며, 서버에 반영된 API는 Swagger가 우선이다.
  - OpenAPI가 나오기 전까지 `docs/api-contract.md`가 클라이언트 쪽 계약 사본이다.
  - 통신 코드 작성 절차는 `docs/api-guide.md`, 계약 규칙은 `.agents/rules/90-backend-contract.md`를 따른다.
- Expo 기반이다. 기술 스택은 Expo SDK 58, React Native 0.88, React 19.3, TypeScript 6(`strict`)다(2026-10-27 `create-expo-app` 기본 템플릿으로 생성).
- Expo는 SDK마다 API가 바뀐다. Expo·EAS·React Native API를 쓰기 전에 `package.json`의 `expo` 메이저 버전을 확인하고, 그 버전의 문서(`https://docs.expo.dev/versions/v58.0.0/`)나 `https://docs.expo.dev/llms.txt`에서 확인한다. 기억에 의존해 쓰지 않는다.
- 패키지 매니저는 `pnpm`만 쓴다. 네이티브 모듈이 포함되거나 Expo SDK 버전에 민감한 패키지는 `pnpm expo install`로 설치한다.
- 라우팅은 Expo Router(파일 기반), 스타일링은 NativeWind(Tailwind 문법)를 쓴다.
- 나머지 스택
  - 서버 상태는 TanStack Query, 전역 클라이언트 상태는 Zustand, HTTP 클라이언트는 axios를 쓴다.
  - 보안 저장소는 `expo-secure-store`, 아이콘은 `lucide-react-native`, 폰트는 Noto Sans KR(`expo-font`)이다.
  - 위치·카메라·지도·AR 같은 네이티브 모듈은 `.agents/rules/85-native-modules.md`를 따른다. 지도·AR SDK는 결정 전이다.
- 공용 UI는 React Native Reusables(RNR, shadcn 규약, `components/ui/`)를 기반으로 하며 `pnpm ui:add https://reactnativereusables.com/r/nativewind/<name>.json`으로 추가한다. 유틸은 `cn`(`@/lib/utils`)이고, 경로 별칭 `@/`는 프로젝트 루트다.
- **설치 상태는 `package.json`이 기준이다.** 2026-10-27 현재 Expo 기본 템플릿 의존성만 있고, 위 나머지 스택(NativeWind·RNR·TanStack Query·Zustand·axios·SecureStore·lucide·jest-expo·MSW)은 아직 설치 전이다. 설치는 `docs/setup.md` 순서대로 하며, MSW 외에는 설치 전에 확인을 요청한다. 설치 전인 도구가 필요한 검증 명령은 "실행 불가(미설치)"로 보고한다.
  - ESLint는 `pnpm lint`로 실행한다.
  - 테스트는 `pnpm test`(jest-expo + React Native Testing Library + MSW)로 실행한다.
  - E2E는 `pnpm e2e`(Maestro)로 실행한다.
  - API 목은 MSW v2다(앱 `msw/native`, jest `msw/node`). v3은 React Native 지원 패키지가 아직 npm에 없어 쓰지 않는다.
- 빌드 전제는 **개발 빌드(expo-dev-client / EAS)**다. `app.json` config plugin(폰트·권한 등)과 네이티브 모듈(지도·AR·카메라)을 쓸 수 있다. Expo Go는 네이티브 모듈을 쓰지 않는 화면의 UI 확인용으로만 쓴다.
- 자동 검사: `.claude/settings.json`의 PostToolUse 훅이 다음을 자동 실행한다.
  - `app/ components/ features/ lib/` 편집 후: `tokens:check`
  - `design/tokens.json` 편집 후: 산출물 재생성
  - `.agents/` 편집 후: 미러 동기화
  - CI 워크플로는 아직 없다(도입 여부는 팀 결정). push 전 검증은 로컬 명령(typecheck·lint·test·tokens:check·harness:check)으로 한다.

## 디렉토리 역할

- `app/` — Expo Router 라우트. 화면 조립, 파라미터 처리, 레이아웃 연결만 담당한다. 라우트가 아닌 파일을 두지 않는다. 인증 여부에 따라 `(auth)`, `(app)` 그룹으로 나누고, 하단 탭 5개(메인 페이지·여행방 관리·AR 팁·기록·지도)는 `(app)/(tabs)`에 둔다.
- `features/<domain>/` — 도메인 단위 코드.
  - 도메인 이름은 API 도메인(오류 코드 접두어)과 1:1로 맞추며, 목록은 `docs/api-guide.md` §2가 기준이다.
  - 하위에 `api/`(`<domain>.api.ts` 도메인 함수, `queries.ts` 키·옵션·훅), `components/`, `model.ts`(DTO → 화면 모델), `errors.ts`를 둔다. 필요하면 `store.ts`, `native/`(그 도메인 전용 네이티브 어댑터)를 둔다.
- `components/ui/` — 도메인과 무관한 공용 UI 컴포넌트. 이름과 variant는 `design/DESIGN.md`의 인벤토리(= Figma 디자인 시스템 컴포넌트명)를 따른다.
- `hooks/`, `lib/` — 도메인 무관 커스텀 훅과 순수 유틸(`money.ts`, `mask.ts`, `date.ts`). `lib/native/`는 위치·지도·카메라 어댑터다. `lib/theme.ts`는 생성 파일이다.
- `api/`
  - `client.ts`: axios 인스턴스·인터셉터
  - `envelope.ts`, `errors.ts`
  - `mocks/`: MSW 핸들러·fixture·`network.ts`(앱)·`server.ts`(jest)
  - `generated/`: OpenAPI 생성물. 백엔드가 스펙을 내보내기 전까지는 없다.
- `design/` — 코드가 읽는 디자인 파일.
  - `tokens.json`: 값. `design.pen` 변수에서 생성한다.
  - `DESIGN.md`: 의도와 Figma 대응표.
  - `design-map.json`: 화면·컴포넌트 ↔ Pencil 노드 id, Figma 참고 위치.
  - `pencil/`: 대안 드래프트.
  - 코드 구현의 확정 원천인 Pencil 작업 파일은 루트의 `design.pen`이다.
- `scripts/` — 하네스·토큰 스크립트. `package.json`의 `tokens:*`, `harness:*` 명령이 호출한다.
- `.maestro/` — Maestro E2E flow(`flows/`, `subflows/`).
- UI는 `components/ui/`의 RNR 컴포넌트(`Text`, `Button`, `Input`, `Card`, `Badge`, `Skeleton`, `Separator`, `Icon`, `Dialog`)와 React Native 코어 컴포넌트(View, Pressable, FlatList 등)를 쓴다. 텍스트는 RN `Text` 대신 `@/components/ui/text`의 `Text`를 쓴다. 웹 DOM 기반 라이브러리(shadcn/ui 웹판, Radix 등)는 동작하지 않으므로 쓰지 않는다.

## 규칙과 문서 적용

- 세부 규칙은 `.agents/rules/`에 분리되어 있다. 작업 대상 경로에 적용되는 규칙을 먼저 읽고 함께 적용한다. 번호가 클수록 도메인 특화 규칙이며, 충돌 시 큰 번호가 우선한다. 여기록 특화 규칙은 다음과 같다.
  - 80: 위치·사진·사용자 콘텐츠
  - 85: 네이티브 모듈
  - 90: 백엔드 계약·MSW
- `.claude/`는 Claude Code용 미러다. `.agents/rules/`, `.agents/skills/`를 수정하면 `pnpm harness:sync`로 미러를 갱신하고 `pnpm harness:check`로 확인한다. `.claude/`를 직접 편집하지 않는다.
- 코드 품질 기준(가독성·예측 가능성·응집도·결합도)은 `.agents/rules/00-baseline.md`에 있다.
- 작업 종류별로 읽는 문서
  - 화면·기능을 만들거나 범위를 판단할 때: `docs/frontend-spec.md`(기능 ID·화면·이동 흐름·비즈니스 규칙·TBD)
  - 서버 통신 코드를 쓸 때: `docs/api-guide.md`(절차·예시)와 `docs/api-contract.md`(엔드포인트·오류 코드·공통 규약)
  - 세 문서는 팀 Notion(기능 명세서 v2, API 명세)을 대조해 만든 요약이다. 원천과 다르면 원천이 이기고 문서를 고친다. 원천 위치와 대조 일시는 각 문서 머리말에 적는다.
- 스킬은 `.agents/skills/`에 `pencil-design`, `yeogirok-ui-patterns` 둘만 있다. 외부 스킬 5종(`frontend-design`, `ui-ux-pro-max`, `vercel-react-best-practices`, `design-system`, `brand`)은 웹 전제 지침이고 규칙과 겹쳐서 2026-09-08 제거했다. 다시 들이지 않는다.
- 사용자 요청과 프로젝트 문서가 충돌하면 사용자 요청을 우선한다. 단, `80-location-media-ugc.md`의 공개 범위·위치 정보·민감 정보 규칙을 완화하는 요청은 위험을 먼저 알린 뒤 진행한다. 문서끼리 충돌하거나 필수 문서를 읽을 수 없으면 임의로 판단하지 말고 그 사실을 보고한다.
- 명세가 "결정 필요(DEC-xx)"로 둔 정책은 임의로 정하지 않는다. 규칙·스킬에 적힌 기본값으로 구현하고, 보고서에 `TBD`로 남긴다.
- 기존 디렉토리 구조, 컴포넌트 패턴, 명명 규칙을 먼저 확인한다. 명확한 이유 없이 새 패턴이나 별도 아키텍처를 도입하지 않는다.

## 디자인 원천 (Figma → Pencil → 토큰)

- 디자인 값의 출처는 팀 Figma `자율 A109` › Design 페이지(디자인 시스템)다. 코드 구현의 확정 원천은 그 값을 옮겨 담은 Pencil 작업 파일 `design.pen`이다.
- 코드는 생성된 `design/tokens.json`(값)과 `design/DESIGN.md`(의도)를 읽는다. UI 작업 전에 두 파일을 읽는다. 이 문서(AGENTS.md)에는 디자인 값을 적지 않는다.
- `design/tokens.json`은 `design.pen` 문서 변수에서 `pnpm tokens:sync`(`scripts/sync-pen-tokens.cjs`)로 동기화하고, 손으로 수정하지 않는다.
  - 토큰을 바꾸려면 Figma 값을 확인하고 Pencil 변수를 바꾼 뒤 동기화한다(`pencil-design` 스킬 절차 C).
  - `tailwind.config.js`, `global.css`, `lib/theme.ts`는 `pnpm tokens:sync`가 생성하므로 직접 편집하지 않는다.
  - `tokens:check`가 `design.pen`·`tokens.json`·산출물·UI 코드의 불일치를 잡는다.
- 사용자 결정(2026-10-27): 폰트 Noto Sans KR, 토큰 이름은 shadcn 규약 유지 + Figma 값, 라이트 고정.
- className에는 시맨틱 토큰만 쓴다(`bg-card`, `text-foreground-secondary`, `bg-primary`, `text-destructive`, `text-h1`, `text-label` …). 프리미티브 팔레트, hex, arbitrary value는 쓰지 않는다. 상세는 `.agents/rules/70-design-tokens.md`에 있다.
- 화면·컴포넌트를 구현하기 전에 `design/design-map.json`에서 Pencil 노드 id를 찾아 Pencil MCP(`Get`·`TakeScreenshot`)로 대조한다(`pencil-design` 스킬 절차 B). 노드가 없으면 `DESIGN.md` 기준으로 구현하고 보고서에 "Pencil 미대조"로 표시한다.
- 새 화면 시안은 `pencil-design` 스킬(절차 A)로 `design.pen`에 그리고 `design-map.json`에 노드 id를 기록한다.
  - Figma Wireframe 페이지(Wxx)는 화면 구성·문구·흐름의 참고 자료다. 와이어프레임 색은 변수와 연결되어 있지 않으므로 값으로 쓰지 않는다.
  - Figma에 쓰는 작업은 사용자가 요청한 범위만 하며, 팀원 레이어를 보호한다.
- 여기록 화면은 `yeogirok-ui-patterns` 스킬의 체크리스트를 따른다. 로딩·빈·오류·권한 필요·처리 중 상태가 없는 화면은 완성으로 보지 않는다.
- UI 작업의 시각적 판단 근거는 Pencil 시안과 `design/DESIGN.md`다. 별도의 디자인 스킬을 읽지 않는다.

## 작업 범위와 승인

- 명세(`docs/frontend-spec.md`)나 요청에 없는 사용자 기능, 화면, 버튼, 필터, 설정을 임의로 추가하지 않는다. P1 이하 기능은 요청이 있을 때만 만들고, 명세의 결정 필요·TBD 항목은 임의로 결정하지 않는다.
- 다음은 기능 완성에 필요한 기본 상태로 간주한다.
  - 로딩, 빈 상태, 오류, 권한 필요, 비활성화
  - 접근성, 다양한 화면 크기 대응
  - 요청 중 중복 실행 방지, 공개 범위·민감 정보 보호
- 요청 범위와 관련 없는 리팩터링, 파일 이동, 이름 변경, 포맷 변경을 함께 하지 않는다.
- 전체 구현을 요청받으면 섹션 단위로 구현하고 검증하되, 중간 승인을 기다리지 않고 요청된 범위까지 완료한다. 사용자가 단계별 검토를 요청한 경우에만 각 단계에서 확인을 기다린다.
- 다음 작업은 시작 전에 확인을 요청한다.
  - 파일 삭제, 대규모 파일 이동, 공개 API 변경
  - 설정 파일(`app.json`, `babel.config.js`, `metro.config.js`)의 광범위한 변경
  - `design/tokens.json` 직접 수정, `api/generated/` 재생성 외 수정
  - 새 패키지 설치·제거·주요 버전 변경·Expo SDK 업그레이드. 단, MSW는 도입이 결정되어 있다.
  - 네이티브 모듈·config plugin 추가

## 완료와 보고

- 코드를 바꾼 뒤에는 `package.json`의 검증 명령을 실행한다.
  - 항상: `pnpm typecheck`, `pnpm lint`
  - UI 변경 시: `pnpm tokens:check`
  - 하네스 변경 시: `pnpm harness:check`
  - `model.ts`나 기존 테스트가 걸린 코드를 고쳤을 때: `pnpm test`
  - 시연 흐름을 바꿨고 기기가 있을 때: `pnpm e2e`
  - 테스트 범위는 `.agents/rules/60-dependencies-tests.md`를 따른다.
- 검증 출력은 요약만 확인한다(`| tail`, 결과 줄 grep). `harness:check`는 통과하면 한 줄만 출력한다. 실패했을 때만 전체 로그를 본다. 같은 디버깅을 두 번 시도해도 해결되지 않으면 진행을 멈추고 사용자에게 보고한다.
- 검증 명령이 없거나 실행할 수 없으면 성공했다고 표현하지 않고, 실행하지 못한 항목과 이유를 보고한다. 네이티브 모듈이 걸린 변경은 실기기 확인 여부를 적는다.
- 오류를 해결하려고 ESLint 규칙, TypeScript 설정, 테스트, 토큰 검사를 비활성화하거나 완화하지 않는다.
- 테스트 통과를 목적으로 기존 테스트를 삭제하거나 검증 수준을 낮추지 않는다.
- 작업 리포트에는 코드나 실행 결과로 확인한 사실만 쓴다. 확인할 수 없는 수치나 결과는 추정하지 않고 `TBD`로 표시한다. UI 작업 보고에는 참조한 Pencil 노드(또는 "미대조")와 토큰 검사 결과를 포함한다.
- 의미 있는 커밋 직후와 세션 마무리 전에는 `docs/worklog-guide.md` 절차로 작업 기록을 갱신한다(트리거: "WORKLOG 갱신해줘"). 로컬 `docs/WORKLOG.md`는 `.gitignore`에 등록된 버퍼이며 커밋하지 않는다.
