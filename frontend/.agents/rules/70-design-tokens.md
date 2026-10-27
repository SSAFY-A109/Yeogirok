---
paths:
  - "app/**"
  - "components/**"
  - "features/**"
  - "design/**"
  - "tailwind.config.js"
  - "global.css"
  - "lib/theme.ts"
  - "components.json"
---

# 디자인 토큰, React Native Reusables, Pencil 연동

## 원천과 산출물

- 디자인 값의 흐름: 팀 Figma `자율 A109` › Design 페이지(디자인 시스템, 값의 출처) → Pencil `design.pen` 문서 변수(확정 원천) → `pnpm tokens:sync` → `design/tokens.json` → 산출물.
- 코드가 읽는 형태는 `design/tokens.json`(값)과 `design/DESIGN.md`(의도)다. `AGENTS.md`나 대화 속 문장, Figma 화면이 아니라 이 파일들을 기준으로 UI를 만든다.
- `design/tokens.json`은 `design.pen` 문서 변수에서 `pnpm tokens:sync`(`scripts/sync-pen-tokens.cjs`)로 동기화하며, 손으로 수정하지 않는다.
  - 토큰을 바꿔야 하면 Figma 디자인 시스템 값을 확인하고 Pencil 변수를 먼저 바꾼다(`SetVariables`, 절차는 `pencil-design` 스킬 C). 그다음 동기화한다.
  - `tokens:check`가 `design.pen`과 `tokens.json`의 불일치를 잡는다. 불일치는 `tokens.json`을 고쳐 맞추지 않고 Pencil을 고쳐서 해결한다.
  - Pencil 변수에 없는 토큰(`radius.2xl·3xl·full`, 타이포, 모션)은 코드 전용이다. 사용자 승인을 받아 `tokens.json`에서 직접 관리한다.
- Figma는 에이전트가 읽어 Pencil로 옮기는 참고 자료다. Figma 와이어프레임의 색·크기는 변수와 연결되어 있지 않으므로 코드 값으로 쓰지 않는다. Pencil → Figma 내보내기 도구는 두지 않는다(2026-10-27 제거, 사용자 결정). Figma 파일에 쓰는 작업은 사용자가 요청한 범위만 한다.
- `tailwind.config.js`, `global.css`, `lib/theme.ts`는 `pnpm tokens:sync`가 생성한다. 이 세 파일은 직접 편집하지 않는다. 생성 로직을 바꿀 때는 `scripts/sync-tokens.cjs`에서 한다.
- UI 코드를 변경한 뒤에는 `pnpm tokens:check`를 실행하고, 통과해야 완료로 본다.

## 토큰 사용

- 색상 className에는 시맨틱 토큰만 쓴다. 이름은 shadcn/React Native Reusables 규약(`X`, `X-foreground`, `X-muted`)을 따르고, 값은 Figma 변수다(사용자 결정 2026-10-27). 대응표와 전체 목록은 `design/DESIGN.md` 2장에 있다.
  - 공통: `bg-background`, `text-foreground`, `text-foreground-secondary`, `text-muted-foreground`, `bg-card`, `bg-primary text-primary-foreground`, `bg-secondary text-secondary-foreground`, `bg-brand-muted`, `bg-disabled text-disabled-foreground`
  - 상태: `text-destructive`, `text-positive`, `bg-positive-muted`, `text-highlight`, `bg-highlight-muted`
  - 테두리: `border-border`, `border-border-strong`, `border-input`, `border-ring`
  - 아이콘: `text-icon`, `text-icon-active`
- 지도 색(`map-route`, `map-pin`, `map-footprint`)은 className이 아니라 `lib/theme.ts`의 `getColors()`로 지도 SDK에 넘긴다.
- 프리미티브 팔레트(`bg-sky-700`, `text-neutral-500`)는 테마에 존재하지 않으므로 쓰지 않는다. `white`, `black`은 모드 무관 고정 토큰으로 허용한다(`text-white`, `bg-black/50`).
- hex, `rgb()`, arbitrary value(`p-[13px]`, `bg-[#fff]`)를 UI 코드에 쓰지 않는다. 필요한 값이 토큰에 없으면 기존 토큰으로 표현할 수 있는지 먼저 검토한다. 그래도 필요하면 Figma 디자인 시스템에 해당 값이 있는지 확인하고, Pencil 변수에 추가한 뒤 동기화한다.
- 라이트 고정이다(사용자 결정 2026-10-27). `dark:` variant를 쓰지 않는다. 테마 전환 UI를 만들지 않는다. `global.css`의 dark 블록은 라이트와 같은 값으로 생성된다.
- 타이포는 토큰 클래스를 쓴다: `text-display`, `text-h1`, `text-h2`, `text-h3`, `text-body`, `text-body-sm`, `text-label`, `text-button`, `text-caption`, `text-micro`.
  - 이 클래스는 크기·행간·자간·굵기를 함께 설정하므로 `text-2xl font-semibold`처럼 조합하지 않는다.
  - Tailwind 기본 `text-sm` 등은 RNR 컴포넌트 내부에서만 쓴다.
  - `text-micro`(11)는 보조 메타 정보에만 쓴다.
- 금액과 숫자 열에는 `tabular-nums` 클래스를 항상 함께 쓴다.
- 간격은 Tailwind 기본 4px 스케일만 쓴다(Figma spacing과 같다).
- 모서리는 `rounded-xs|sm|md|lg|xl|2xl|3xl|full`만 쓴다. 버튼·입력은 `rounded-md`, 카드는 `rounded-lg`, 바텀시트는 `rounded-xl`, 칩·배지는 `rounded-full`이다.
- 크기 토큰 `h-touch`, `h-button-sm`, `h-button-md`, `h-button-lg`, `h-input`, `w-icon`, `w-icon-sm`을 우선한다.
- 그림자는 쓰지 않는다(Figma에 정의 없음). 카드 구분은 `border-border`로 한다.
- className으로 표현할 수 없는 값(StatusBar, 내비게이션 테마, 지도 색, Reanimated 보간)은 `lib/theme.ts`의 `getColors(scheme)`, `typography`, `radius` 등을 쓴다.

## React Native Reusables (components/ui)

- 공용 UI의 기반은 React Native Reusables(RNR, shadcn 규약)다. `components.json`이 설정되어 있다. 새 컴포넌트는 `pnpm ui:add https://reactnativereusables.com/r/nativewind/<name>.json`으로 추가한다. `npx @react-native-reusables/cli add`는 pnpm 빌드 승인 경고에 걸려 실패할 수 있으므로 위 명령을 쓴다.
- 추가된 파일은 벤더 파일이다. 첫 줄의 `// vendor: react-native-reusables` 주석을 유지하고, 원본은 최대한 수정하지 않는다. 프로젝트 수정이 필요하면 해당 줄에 `// project:` 주석을 남긴다. `tokens:check`는 벤더 파일에서 arbitrary value·프리미티브 팔레트 검사를 면제하지만, hex와 `StyleSheet.create`는 검사한다.
- 텍스트는 React Native `Text` 대신 `@/components/ui/text`의 `Text`를 쓴다(`font-sans`로 Noto Sans KR이 적용된다). 아이콘은 `@/components/ui/icon`의 `Icon as={LucideIcon}`을 쓴다.
- RNR `Text`의 `variant`(`h1`~`h4`, `lead`, `muted` 등)는 웹 기본값(`text-4xl font-extrabold` 등)이므로 화면 코드에서 쓰지 않는다. 크기·굵기는 타이포 토큰 클래스(`className="text-h1"`)로만 지정한다. `variant`는 RNR 내부 컴포넌트(`CardTitle` 등)가 쓰는 것만 허용한다.
- 버튼은 RNR `Button`을 쓰고, 새 버튼 컴포넌트를 만들지 않는다. Figma `Action / Button`과의 대응은 다음과 같다.
  - Style Primary → `variant="default"`, Secondary → `"secondary"`, Outline → `"outline"`
  - Size Large → `size="lg"` + `h-button-lg`, Medium → `h-button-md`
  - 비활성 → `disabled`
  - 주요 CTA는 하단에 고정한 Large다.
- RNR에 없는 컴포넌트는 RNR 컴포넌트를 조합해 `components/ui/`에 kebab-case 파일로 만든다. 대상은 `SearchField`, `FilterChip`, `EmptyState`, `BottomSheet`, `Toast`, `TabBar`, `PermissionGate`이고, 이름과 variant는 `design/DESIGN.md` 5장 인벤토리를 따른다.
- 경로 별칭 `@/`는 프로젝트 루트다(`tsconfig.json` paths).

## 컴포넌트와 Pencil·Figma 대응

- 공용 컴포넌트의 이름, variant, size는 `design/DESIGN.md` 5장 인벤토리를 그대로 따른다. 인벤토리는 Figma 디자인 시스템 컴포넌트 이름과 같다.
- 화면을 구현하기 전에 `design/design-map.json`에서 해당 화면·컴포넌트의 `pencil` 노드를 찾는다.
  - 노드가 있으면 Pencil MCP `Get`(프레임 자체의 fill·layout·크기까지)과 `TakeScreenshot`으로 대조한 뒤 구현한다(`pencil-design` 스킬 절차 B).
  - `없음`이면 `DESIGN.md` 기준으로 구현하고 보고서에 "Pencil 미대조"로 표시한다. `figma` 필드의 와이어프레임(Wxx)은 구성·문구 참고로만 쓴다.
- Pencil에 없는 컴포넌트를 새로 만들 때는 `DESIGN.md` 인벤토리와 `design-map.json`에 항목을 추가한다(`pencil: "없음"`).
- Pencil 아트보드 이름은 `Wxx 화면명 · 상태`이며, 번호는 Figma 와이어프레임과 같다(`pencil-design` 스킬).

## 보고

- UI 작업 보고에는 다음을 포함한다.
  - 참조한 Pencil 노드(`이름 (id)` 또는 "미대조")와 참고한 Figma 위치(Wxx)
  - `pnpm tokens:check` 결과
  - 새로 추가·요청한 토큰 목록
  - 새로 추가한 RNR 컴포넌트
