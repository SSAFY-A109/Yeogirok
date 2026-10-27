# 디자인 스펙 (DESIGN.md)

> 이 문서는 UI 작업의 **원천 문서**다. 값의 원천은 `design/tokens.json`이며, 이 문서는 그 값을 어떤 의도로 쓰는지 설명한다.
> 값의 출처는 팀 Figma `자율 A109` › **Design 페이지**(변수 3개 컬렉션 66개, 텍스트 스타일 9개, 컴포넌트 4종)이고, 확정 원천은 그 값을 옮겨 담은 Pencil(`design.pen`) 문서 변수다. 흐름: Figma Design 페이지 → Pencil 변수 → `pnpm tokens:sync` → `tokens.json` → 산출물. 코드는 Figma를 직접 읽지 않는다.
> 사용자 결정(2026-10-27): 폰트 **Noto Sans KR**, 토큰 이름은 **shadcn 규약 유지 + Figma 값**, **라이트 고정**(다크 모드 없음).
>
> 상태: **Figma 디자인 시스템 v1.0 반영 · Pencil 변수 49개 동기화 · 화면 아트보드 없음**

## 1. 제품 성격과 디자인 톤

- 서비스: 여기록 — 다른 사람의 여행 코스·발자취에서 장소를 가져와 내 여행을 만들고, 일행과 완성한 여행기를 다시 코스로 공유한다. 주요 상황은 친구들과 떠나는 1박 2일 여행이다.
- Figma 디자인 시스템의 주제는 "여행의 순간을 잇는 디자인"이다. 기준은 iOS·Android 390px, **White-first**(흰 바탕이 기본)다.
- 색의 역할(Figma §01):
  - **스카이 블루**(`#49B5E7`): 브랜드와 지도
  - **딥 블루**(`#1A73A3`): 버튼·선택 같은 인터랙션
  - **코랄**(`#FFAA8D`·`#A84729`): 발자취의 기억
- 화면 최상위 배경은 흰색이고, 보조 영역만 `muted`(`#F7F9FA`)를 쓴다.
- 그라데이션은 쓰지 않는다(Figma 사용 규칙 04). 코랄은 발자취·하이라이트·작은 안내 요소에 제한적으로 쓴다(사용 규칙 03).
- 그림자는 정의하지 않는다(Figma effect 스타일 0개). 카드 구분은 `border-border` 테두리로 한다.
- 아이콘은 `lucide-react-native` 단일 세트를 쓰고, `components/ui/icon.tsx`의 `Icon`으로 감싼다. 이모지 아이콘은 쓰지 않는다.

## 2. 색상 토큰 (shadcn 이름 ← Figma 변수)

시맨틱 토큰만 사용한다. 프리미티브(`sky-700`, `neutral-200`)는 `tailwind.config.js`에 노출되지 않는다. 예외: `white`, `black`(모드 무관 고정값, `black`은 오버레이 `bg-black/50` 전용).

규약: `X` = 배경, `X-foreground` = 그 위의 글자, `X-muted` = 옅은 배경.

| 코드 토큰 | Figma 변수 | 값 | 용도 · className 예 |
|---|---|---|---|
| `background` | `bg/canvas` | `#FFFFFF` | 화면 배경 `bg-background` |
| `foreground` | `text/primary` | `#14191F` | 기본 글자 `text-foreground` (17.67:1) |
| `foreground-secondary` | `text/secondary` | `#414A53` | 본문 보조 글자 `text-foreground-secondary` (9.02:1) |
| `muted-foreground` | `text/muted` | `#66717D` | 플레이스홀더·보조 설명 (4.97:1) |
| `card` · `popover` (+`-foreground`) | `bg/surface` · `text/primary` | `#FFFFFF` · `#14191F` | 카드·목록 항목 / 다이얼로그·바텀시트 |
| `muted` | `bg/subtle` | `#F7F9FA` | 보조 영역·스켈레톤 `bg-muted` |
| `primary` · `primary-foreground` | `action/primary` · `text/on-brand` | `#1A73A3` · `#FFFFFF` | 주요 버튼·링크 (5.22:1). RNR `Button` default |
| `primary-pressed` | `action/primary-hover` | `#145C83` | 주요 버튼 눌림 상태(RN에는 hover가 없다) |
| `secondary` · `secondary-foreground` | `action/secondary` · `text/brand` | `#EFF9FE` · `#1A73A3` | 보조 버튼 |
| `brand-muted` | `bg/brand-soft` | `#EFF9FE` | 선택된 칩·Brand 배지 배경 |
| `accent` · `accent-foreground` | `bg/selected` · `text/brand` | `#D9F1FC` · `#1A73A3` | 눌림·선택 배경 |
| `disabled` · `disabled-foreground` | `action/disabled` · `text/muted` | `#E6EAEE` · `#66717D` | 비활성 버튼(4.11:1 — 비활성은 대비 기준 예외) |
| `destructive` · `destructive-foreground` | `status/error` · (없음) | `#CB4646` · `#FFFFFF` | 오류·삭제 (흰 배경 4.66:1) |
| `positive` · `positive-muted` | `status/success` · (없음) | `#178663` · `#EAF7F1` | 완료·성공. 배지 배경은 Figma 직접 입력값 `[확인]` |
| `highlight` · `highlight-muted` | `text/coral` · `bg/coral-soft` | `#A84729` · `#FFF4EF` | 발자취 강조 (5.40:1) |
| `border` · `input` | `border/default` | `#E6EAEE` | 구분선·카드·입력 테두리 |
| `border-strong` | `border/strong` | `#D4DAE0` | 강조 구분선 |
| `ring` | `border/selected` | `#1A73A3` | 포커스·선택 테두리 |
| `icon` · `icon-active` | `icon/default` · `icon/active` | `#414A53` · `#1A73A3` | 아이콘 `text-icon` |
| `map-route` | `map/route` | `#49B5E7` | 지도 경로선 — `lib/theme.ts`로 전달 |
| `map-pin` | `map/pin` | `#1A73A3` | 장소 핀 |
| `map-footprint` | `map/footprint` | `#FFAA8D` | 발자취 핀 |

Figma Semantic Colors 26개는 위 표에 모두 대응된다. `destructive-foreground`와 `positive-muted` 2개는 Figma에 변수가 없어 코드 쪽에서 정한 값이다.

### 사용 규칙 (Figma Design 페이지 §04)

1. 화면 최상위 배경은 흰색이고, 보조 영역만 `muted`를 쓴다.
2. 스카이(`#49B5E7`) 배경에는 흰 작은 글자를 쓰지 않는다(2.32:1). 흰 버튼의 글자는 딥 블루를 쓴다.
3. 코랄은 발자취·하이라이트·작은 안내 요소에만 제한적으로 쓴다.
4. 입력 테두리는 `input`, 선택 상태는 `ring`(딥 블루)이다. 그라데이션은 쓰지 않는다.
5. 터치 영역은 최소 44px, 터치 요소 사이 간격은 8px 이상이다.

### 대비 주의 (WCAG, 2026-10-27 계산)

- 지도 위 **발자취 핀** `map-footprint`의 흰 배경 대비는 1.84:1이고, 경로선 `map-route`는 2.32:1이다. 그래픽 요소 기준 3:1에 못 미치므로 핀에는 흰 테두리와 그림자를, 경로선에는 3px 이상 두께를 준다. 색만으로 의미를 전달하지 않는다.
- `positive` 글자를 `positive-muted` 배경 위에 쓰면 4.12:1로, 12px 글자 기준(4.5:1)에 못 미친다. 배지 글자를 키우거나 배경 값을 Figma에서 정한 뒤 갱신한다 `[확인]`.

### 라이트 고정

- 다크 모드는 없다(사용자 결정 2026-10-27). 앱 설정에서 `userInterfaceStyle: "light"`로 고정한다.
- `global.css`의 `.dark:root` 블록은 라이트와 같은 값으로 생성된다. `dark:` variant를 쓰지 않는다.
- Figma에 다크 값이 생기면 Pencil 변수에 테마 값을 추가하고 동기화한다(`pencil-design` 절차 C).

## 3. 타이포그래피

- 서체는 **Noto Sans KR** 단일 패밀리이고, Regular 400 · Medium 500 · Bold 700을 쓴다. `expo-font` config plugin으로 세 굵기를 등록하며 개발 빌드에서 적용된다. Pencil도 Google 폰트를 렌더하므로 캔버스에서 실제 서체로 보인다.
- `components/ui/text.tsx`의 `Text`는 기본 클래스에 `font-sans`를 포함한다. 그래서 모든 글자는 이 `Text`를 쓴다.
- 자간은 0이다(Figma 그대로).
- 화면 제목은 20~24, 본문은 14~16이다(Figma §02).
- 시스템 글꼴 크기 조정을 존중한다. 숫자·금액이 많은 영역은 `maxFontSizeMultiplier={1.3}`로 레이아웃 붕괴를 막되, 조정을 끄지는 않는다.
- 금액(영수증 합계 등)과 숫자 열에는 `tabular-nums`를 함께 쓴다.

| 클래스 | Figma 스타일 | 크기/행간 | 굵기 | 용도 |
|---|---|---|---|---|
| `text-display` | 여기록/Display/28 | 28/40 | 700 | 온보딩·완성 화면 헤드라인 |
| `text-h1` | 여기록/Heading/24 | 24/34 | 700 | 화면 제목 |
| `text-h2` | 여기록/Heading/20 | 20/30 | 700 | 섹션 제목 |
| `text-h3` | 여기록/Title/18 | 18/28 | 700 | 카드 제목 |
| `text-body` | 여기록/Body/16 | 16/26 | 400 | 기본 본문 |
| `text-body-sm` | 여기록/Body/14 | 14/22 | 400 | 보조 본문 |
| `text-label` | 여기록/Label/14 | 14/20 | 500 | 라벨·탭·칩 |
| `text-button` | (Label/14) | 14/20 | 500 | 버튼 — Figma에 버튼 스타일이 없어 Label/14를 씀 `[확인]` |
| `text-caption` | 여기록/Caption/12 | 12/18 | 400 | 날짜·상태 |
| `text-micro` | 여기록/Micro/11 | 11/16 | 500 | 보조 메타 정보만. 필수 정보에는 쓰지 않는다 |

## 4. 간격 · 형태 · 크기

- **간격**: Figma spacing(4·8·12·16·20·24·32·40·48)은 Tailwind 4px 스케일(`1·2·3·4·5·6·8·10·12`)과 같으므로 Tailwind 기본 스케일을 쓴다.
  - 화면 좌우 여백은 `px-5`(20)이다.
  - 콘텐츠 사이는 `gap-4`~`gap-6`(16~24)이다.
- **모서리**(Figma radius):
  - `rounded-xs` 6
  - `rounded-sm` 8
  - `rounded-md` 12: 버튼·입력
  - `rounded-lg` 16: 카드
  - `rounded-xl` 20: 바텀시트
  - `rounded-full`: 칩·배지
  - `2xl`·`3xl`은 RNR 호환용 코드 전용이다.
- **크기**(Figma control):
  - `h-button-sm` 36
  - `h-button-md` 44: 일반 버튼
  - `h-button-lg` 52: 주요 CTA
  - `h-input` 52: 검색·입력
  - `min-h-touch min-w-touch` 44: 최소 터치 영역
  - 아이콘은 `w-icon` 24, `w-icon-sm` 20이다(코드 기본값).
- **모션**: `duration-fast/base/slow` = 150/220/320ms. 화면 전환은 플랫폼 기본을 쓴다.

## 5. 컴포넌트 인벤토리

### 5-1. React Native Reusables (`components/ui/`)

`pnpm ui:add https://reactnativereusables.com/r/nativewind/<name>.json`으로 추가한다. 추가한 뒤에는 `pnpm tokens:check`를 통과하도록 arbitrary value만 조정하고, 나머지는 원본을 유지한다.

| 파일 | 내보내기 | 비고 |
|---|---|---|
| `text.tsx` | `Text`, `TextClassContext` | 기본 클래스에 `font-sans` 추가(프로젝트 수정) |
| `button.tsx` | `Button`, `buttonVariants` | Figma `Action / Button` 대응(5-2) |
| `input.tsx` | `Input` | `border-input bg-background` |
| `card.tsx` | `Card` 외 | `bg-card` + `border-border`(그림자 없음) |
| `badge.tsx` | `Badge` | Figma `Feedback / Badge` 대응 |
| `skeleton.tsx` | `Skeleton` | `bg-muted` |
| `separator.tsx` | `Separator` | |
| `icon.tsx` | `Icon` | lucide 래퍼 |
| `dialog.tsx` | `Dialog` 외 | 루트 레이아웃의 `PortalHost` 필요 |

### 5-2. Figma 디자인 시스템 대응 (Design 페이지 컴포넌트 4종)

| Figma 컴포넌트 (노드) | 코드 | 대응 |
|---|---|---|
| `Action / Button` (418:440) | RNR `Button` | Style Primary → `variant="default"`, Secondary → `"secondary"`, Outline → `"outline"`. Size Medium → 기본 + `h-button-md`, Large → `size="lg"` + `h-button-lg`. State Disabled → `disabled`(`bg-disabled text-disabled-foreground`). 모서리 `rounded-md` |
| `Search / TextField` (418:454) | `components/ui/search-field.tsx` (RNR `Input` 조합) | State Default·Focused(`border-ring`)·Error(`border-destructive` + 필드 아래 오류 문구). 높이 `h-input`, `rounded-md`, 좌우 패딩 16 |
| `Selection / FilterChip` (418:471) | `components/ui/filter-chip.tsx` | Size Small(`text-caption`)·Medium(`text-label`) × Default(`bg-card border-border text-foreground-secondary`)·Selected(`bg-brand-muted border-ring text-secondary-foreground`)·Disabled(`bg-muted text-muted-foreground`). `rounded-full`. 시각 높이가 44보다 작으면 `hitSlop`으로 터치 영역 44를 확보 |
| `Feedback / Badge` (418:484) | RNR `Badge` + 프로젝트 variant | Tone Neutral(`bg-muted text-foreground-secondary`)·Brand(`bg-brand-muted text-secondary-foreground`)·Coral(`bg-highlight-muted text-highlight`)·Success(`bg-positive-muted text-positive`). `text-caption`, `rounded-full` |

### 5-3. 프로젝트 전용

| 컴포넌트 | 코드 | 비고 |
|---|---|---|
| `TabBar` | `components/ui/tab-bar.tsx` | 탭 5개(6장). Figma Wireframe 페이지의 `공통 / 하단 탭` 참고 |
| `EmptyState` | `components/ui/empty-state.tsx` | 아이콘·제목·설명·액션 1개. 빈 목록·오류·권한 필요 |
| `BottomSheet` | `components/ui/bottom-sheet.tsx` | `bg-popover rounded-t-xl`. 지도 위 목록·여행방 선택 |
| `Toast` | `components/ui/toast.tsx` | Figma에 없음 — 시안이 생기면 토큰을 정한다 `[확인]` |
| `PermissionGate` | `components/ui/permission-gate.tsx` | 위치·카메라·사진 권한 사전 안내와 거부 상태(규칙 80, `yeogirok-ui-patterns`) |

컴포넌트가 코드에 없으면 위 이름과 variant로 만든다. 이름을 바꾸면 Pencil과 이 표, `design-map.json`을 함께 고친다.

### 5-4. Figma 컴포넌트에서 정리가 필요한 점 (2026-10-27 확인, Figma는 수정하지 않음)

코드는 아래 정리된 값을 따르고, Figma 수정은 디자이너에게 요청한다.

1. `Search / TextField` Error 상태의 테두리가 Default와 같다(`border/default`). 코드는 `border-destructive`와 오류 문구를 쓴다.
2. 버튼 높이가 실제 39px(M)·48px(L)로, 크기 변수(44·52)와 터치 기준 44보다 작다. Outline 변형만 2px 더 크다. 코드는 크기 토큰을 따른다.
3. 컴포넌트 글자에 텍스트 스타일이 연결되어 있지 않다. Button L의 15px는 스타일에 없는 크기다. 코드는 `text-button`(14)을 쓴다.
4. Badge Success 배경이 변수 없이 `#EAF7F1`로 들어가 있다. 글자 대비는 4.12:1이다.
5. Wireframe 페이지의 색이 변수와 연결되어 있지 않다(단색 채우기 3,731개 중 0개). 디자인 시스템 컴포넌트 인스턴스도 0개다. 와이어프레임의 색은 참고만 하고, 값은 이 문서를 따른다.

## 6. 화면 패턴

- 하단 탭은 5개다(기능 명세서 v2 §3, Figma 사용자 흐름 10/23).
  1. **메인 페이지**: 코스 검색·추천, My 진입
  2. **여행방 관리**
  3. **AR 팁**: 카메라 화면을 채우고 우측 상단에 지도 오버레이
  4. **기록**
  5. **지도**: 발자취 방문 분포도, 발자취 남기기
  - 중앙 + 메뉴는 없다. My는 탭이 아니라 메인 페이지에서 진입한다.
- 화면 제목은 `text-h1` 하나다. 헤더 규칙은 `.agents/rules/40-styling-accessibility.md`를 따른다.
- 로딩은 스피너보다 `Skeleton`을 쓴다. AI 초안·업로드처럼 1초 이상 걸리는 작업은 진행 상태를 단계로 보여준다.
- 오류는 발생한 범위에서 원인과 재시도로 보여준다.
- 권한이 필요한 기능(지도의 발자취, AR 팁, 사진·영수증 업로드)은 진입할 때 사전 안내를 거친다.
- AI가 만든 결과(일정 초안, 기록 초안)는 "초안"임을 표시하고, 사용자가 확인·수정한 뒤에 확정한다.
- 화면별 체크리스트는 `yeogirok-ui-patterns` 스킬에 있다.

## 7. 동기화 섹션 (Pencil이 원천 — `pencil-design` 절차 C가 갱신)

| 항목 | 값 |
|---|---|
| 값의 출처 | Figma `자율 A109` › Design 페이지(414:485) — 변수 `여기록 · Primitives`(22)·`Semantic Colors`(26, Light)·`Layout`(18), 텍스트 스타일 9개. 2026-10-27 확인 |
| 확정 원천 | `design.pen`(Pencil) 문서 변수 49개 = 시맨틱 색 34(라이트 단일 값) + white/black + radius-xs~xl + size 7개 + `$font-sans`(Noto Sans KR). 화면 아트보드는 아직 없다(KeyFin 아트보드 2026-10-27 제거) |
| 토큰 동기화 | `pnpm tokens:sync` = `scripts/sync-pen-tokens.cjs`(design.pen 변수 → tokens.json, 같은 hex는 프리미티브 참조) → `scripts/sync-tokens.cjs`(→ tailwind.config.js · global.css · lib/theme.ts). 불일치는 `tokens:check`·`harness:check`가 잡는다. CI는 두지 않는다(2026-09-01 결정) |
| 마지막 동기화 | `tokens.json` `$source.pencilSyncedAt` |
| 구현 전 대조 | `design/design-map.json`의 Pencil 노드 id → MCP `Get`·`TakeScreenshot`(`pencil-design` 절차 B) |
| Pencil 변수에 없는 토큰 | `radius.2xl·3xl·full`, 타이포 10종, 모션 — 코드 전용. 승인 후 `tokens.json`에서 직접 관리 |
| Figma 내보내기 | 없음 — KeyFin 때 만든 `pnpm figma:build`(Pencil → Figma)는 2026-10-27 제거했다(사용자 결정: 유지 금지). 방향은 Figma → Pencil 한쪽뿐이다 |
