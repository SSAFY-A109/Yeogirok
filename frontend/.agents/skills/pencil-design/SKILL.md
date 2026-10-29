---
name: pencil-design
description: Pencil(pen.dev) design.pen이 코드 구현의 확정 원천이다. 화면·컴포넌트 구현 전 Pencil 노드 대조(절차 B), Figma 와이어프레임을 참고한 새 화면 드래프트(절차 A), Figma 디자인 시스템 값 → Pencil 변수 → design/tokens.json 동기화(절차 C)를 다룬다. UI 구현, 화면 시안, 토큰 변경, "디자인대로 만들어줘" 요청에 사용한다.
metadata:
  author: project
  version: "3.0.0"
---

# pencil-design

**Pencil(`design.pen`)이 코드 구현의 단일 확정 원천이다.** 코드는 Pencil 아트보드를 대조해 구현하고, 토큰은 Pencil 문서 변수에서 동기화한다.

팀 Figma `자율 A109`는 Pencil의 **상류 참고 자료**다.
- Design 페이지(디자인 시스템): 변수 값의 출처
- Wireframe 페이지(Wxx): 화면 구성·문구·흐름의 출처

에이전트는 Figma를 읽어 Pencil로 옮기고, 코드는 Figma를 직접 읽지 않는다.

## 언제 쓰는가

- 화면·컴포넌트를 구현하기 전(매 UI 작업): 절차 B
- 아직 없는 화면의 시안이 필요할 때, 레이아웃 대안 비교, 플로우 시안: 절차 A
- Figma 디자인 시스템 값이 바뀌었거나 색·반경·크기·서체 토큰을 바꿀 때: 절차 C
- "이 화면 어떻게 생길지 그려줘", "Pencil대로 만들어줘", "토큰 바꿔줘" 류의 요청

## 전제

- Pencil 에디터에 `design.pen`이 열려 있어야 MCP가 응답한다. 응답이 없으면 파일을 열어 달라고 요청한다.
- 작업을 시작할 때 순서대로 호출한다.
  1. `mcp__pencil__get_app_state`: 열린 파일·선택 상태·최상위 노드 확인
  2. `mcp__pencil__read_skill`: Pencil 공식 스킬(`.pen` 스키마·`execute` API). 조작법의 원천이며, 이 문서는 프로젝트 규약만 다룬다.
- `design.pen`은 평문 JSON이다. **에이전트는 MCP(`execute`의 `Get`·`TakeScreenshot`)로 읽는다.** 파일을 직접 읽는 것은 빌드·검사 스크립트(`scripts/sync-pen-tokens.cjs`, `scripts/check-harness.cjs`)뿐이다. 아트보드가 늘면 파일이 커지므로 Read·Grep으로 통째로 열지 않는다.
- 서체 `$font-sans`는 Noto Sans KR이다. Google 폰트라 Pencil 캔버스에서도 실제 서체로 렌더된다.
- Figma를 읽을 때(절차 A·C)는 읽기만 한다. Figma에 쓰는 작업은 사용자가 요청한 경우에만 하고, 프로젝트의 Figma 작업 규칙을 따른다: 팀원 수정분 보호, 네이티브 요소와 Auto layout, 요청 범위만.

## 프로젝트 규약

### 파일과 아트보드

- 메인 작업 파일은 리포 루트의 `design.pen`이다. 대안 탐색만 `design/pencil/<flow>-<screen>.pen`으로 분리한다.
- 구현 기준 아트보드 이름은 `Wxx 화면명 · 상태`다(`W09 여행방 상세`, `W09 여행방 상세 · 오류`).
  - 번호는 Figma Wireframe 페이지의 화면 번호와 같게 한다.
  - 대안은 `· 대안 1`을 붙인다.
  - `design/design-map.json`의 `screens` 키(라우트 기준 영문)와는 각 항목의 `pencil` 필드로 연결한다.
- 프레임은 390×844, `clip: true`, `layout: vertical`이다. 안전 영역은 상단 59, 하단 34다.
- 라이트 고정이다(사용자 결정 2026-10-27). 다크 아트보드를 만들지 않는다.

### 토큰과 변수

- 문서 변수 56개의 구성(2026-10-29)
  - `tokens.json`의 시맨틱 색 39개(라이트 단일 값). Figma Semantic Colors 외에 Layout 컬렉션의 색 `badge/correct`·`font/select`도 포함한다
  - `white`/`black`
  - `radius-xs·sm·md·lg·xl`
  - `size-touch·button-sm·button-md·button-lg·input·chip-sm·chip-md·icon·icon-sm`
  - `font-sans`
- 새 변수를 추가하면 `tokens.json`에도 같은 이름의 항목을 추가한다(승인 필요). `sync-pen-tokens.cjs`는 기존 항목의 값만 갱신한다.
- 변수 이름이 곧 `tokens.json` 경로다(`primary` → `semantic.color.primary`, `radius-md` → `semantic.radius.md`).
- 이름은 shadcn 규약이고, 값은 Figma 변수다. 대응표는 `design/DESIGN.md` §2에 있다(`$primary` = Figma `action/primary` = `#1A73A3`).
- 도형·텍스트 색은 `$변수`만 참조한다(`fill: "$primary"`). 임의 hex를 아트보드에 직접 찍지 않는다.
- 텍스트는 `fontFamily: "$font-sans"`와 `DESIGN.md` 3장의 크기·굵기·행간을 쓴다(`h1` 24/34 700, `body` 16/26 400, `label` 14/20 500 …).
- 간격은 4의 배수다. 좌우 여백 20, 콘텐츠 사이 16~24. 모서리는 `$radius-*`만 쓴다.
- 새 색·크기가 필요하면 Figma 디자인 시스템에 있는지 먼저 확인한다. 있으면 **변수를 먼저 추가**(`SetVariables`)하고 절차 C로 동기화한다. 없으면 사용자에게 Figma에 추가할지 묻는다. 변수 없이 값을 쓰지 않는다.

### 컴포넌트

- `DESIGN.md` 5장 인벤토리 이름으로 프레임을 만든다.
  - Figma 컴포넌트 이름(`Action / Button`, `Selection / FilterChip` …)과 variant 이름(`Size=Large, Style=Primary, State=Default`)을 그대로 쓴다.
- 같은 컴포넌트를 두 번 이상 쓰면 Pencil 컴포넌트(`reusable: true`)로 만들고 `ref`로 배치한다.
- 아이콘은 `icon` 노드(`library: "lucide"`, `icon: "<lucide 이름>"`)를 쓴다. 이모지나 텍스트 라벨로 대체하지 않는다.

### 여기록 화면에서 반드시 표현할 것

- 상태 아트보드를 최소 하나씩 그린다.
  - 로딩(스켈레톤)
  - 빈 상태
  - 오류(재시도 버튼)
- 권한 상태: 위치·카메라가 필요한 화면은 권한 사전 안내와 거부 상태를 그린다.
- AI 결과: 생성 중 / 초안(확인 필요 표시) / 실패를 그린다.
- 지도: 경로 `$map-route`, 장소 핀 `$map-pin`, 발자취 핀 `$map-footprint`(흰 테두리 포함)를 쓴다. 방문 분포는 집계 표시만 하고, 개인 방문자나 동선을 그리지 않는다.
- 주요 CTA는 하단 고정, 높이 `$size-button-lg`(52), 안전 영역 위에 둔다.

## 절차 A: 새 화면 드래프트

1. `design-map.json`의 `screens`에 키가 있는지 확인한다. 없으면 라우트와 함께 추가한다(`pencil: "없음"`, `figma: "Wxx"`).
2. Figma Wireframe 페이지에서 해당 화면(Wxx)을 읽는다(구성·문구·흐름). 와이어프레임의 색은 변수와 연결되어 있지 않으므로 값으로 쓰지 않는다.
3. `get_app_state` → `read_skill`.
4. 토큰 변수가 파일에 있는지 확인한다(현재 56개 — `Print(GetVariables())`).
5. `FindEmptySpace`로 빈자리를 잡고 아트보드를 그린다. 대안은 가로로 나란히 둔다.
6. `TakeScreenshot`으로 확인해 사용자에게 보여주고 방향을 확정한다.
7. 확정되면 `design-map.json`의 `screens.<key>.pencil`에 `Wxx 화면명 (노드id)`를 기록한다. 이 기록이 절차 B의 입력이다.

## 절차 B: 구현 전 대조 (매 UI 작업)

1. `design-map.json`에서 대상 화면·컴포넌트의 `pencil` 노드 id를 찾는다. `없음`이면 `DESIGN.md` 기준으로 구현하고 보고서에 **"Pencil 미대조"**로 표시한다.
2. `execute`로 구조를 읽는다. **프레임 자신의 `fill`·`layout`·크기까지 읽는다**(자식만 읽고 프레임 배경을 놓친 전례가 있다).
   ```js
   Print(Get("<id>", {depth: 2}))
   Get("<id>", (n, c) => Print(n.id, "|", n.name, "|", n.type, "|", Math.round(c.bounds.x), Math.round(c.bounds.y), Math.round(c.bounds.width), Math.round(c.bounds.height), "|", JSON.stringify(n.fill ?? "")))
   TakeScreenshot(["<id>"])
   ```
3. 값을 옮긴다.
   - 색: `$변수` 이름을 그대로 시맨틱 토큰 클래스로 옮긴다(`$primary` → `bg-primary`, `$muted-foreground` → `text-muted-foreground`).
   - 크기·모서리: `$size-*`·`$radius-*` → `h-button-lg`·`rounded-md`.
   - 글자: 크기·굵기는 `DESIGN.md` 3장 표로 타이포 토큰(`text-h1` …)에 대응시킨다.
4. Pencil 값이 `tokens.json`에 없으면 **변수 추가 → 절차 C** 순으로 처리한다. 그 전까지는 가장 가까운 토큰으로 구현하고, 보고서에 적는다.
5. 구현 후 시뮬레이터나 기기 스크린샷을 Pencil 스크린샷과 나란히 놓고 배경·헤더·탭바·간격을 대조한다.
6. 보고서에 참조한 Pencil 노드(`이름 (id)`)와 `tokens:check` 결과를 적는다.

## 절차 C: 토큰 동기화 (Figma 값 → Pencil 변수 → tokens.json)

1. Figma Design 페이지의 변수(`여기록 · Primitives`·`Semantic Colors`·`Layout`)와 텍스트 스타일을 읽고, 바뀐 값을 `DESIGN.md` §2 대응표로 Pencil 변수 이름에 맞춘다.
2. Pencil 변수를 바꾼다(`SetVariables({...})`). 사용자가 Pencil 앱에서 직접 바꿔도 된다.
3. Pencil이 디스크에 저장했는지 확인한다. 변수 변경은 저장이 늦을 수 있다.
   - 확인 명령: `node -e 'console.log(JSON.parse(require("fs").readFileSync("design.pen","utf8")).variables["<이름>"])'`
   - 저장되지 않았으면 사용자에게 ⌘S를 요청한다.
4. `pnpm tokens:sync`를 실행한다.
   - `scripts/sync-pen-tokens.cjs`가 `design.pen` 변수를 `tokens.json`에 반영한다(같은 hex의 프리미티브가 있으면 참조로). 그리고 `$source.pencilSyncedAt`을 갱신한다.
   - 이어서 `sync-tokens.cjs`가 `tailwind.config.js`·`global.css`·`lib/theme.ts`를 다시 만든다.
   - Figma에 새 프리미티브가 생겼으면 `tokens.json` `primitive.color`에도 추가한다(승인 필요).
5. `pnpm tokens:check`, `pnpm typecheck`를 실행한다. 사라진 토큰을 참조하는 코드가 있으면 목록을 보고하고, 승인 후 수정한다.
6. `tokens:check`는 `design.pen`과 `tokens.json`이 다르면 실패한다. `tokens.json`을 손으로 고쳐 맞추지 말고 Pencil을 고친다. CI는 없으므로 이 검사는 훅과 로컬 명령으로만 돈다.

## 금지

- `tokens.json`, `tailwind.config.js`, `global.css`, `lib/theme.ts`를 직접 편집하지 않는다(원천은 Pencil, 생성은 스크립트).
- 대조 없이 "Pencil대로 구현했다"고 보고하지 않는다. 노드 id와 스크린샷이 없으면 "미대조"다.
- Pencil MCP가 응답하지 않는 상태에서 그리거나 대조한 것처럼 보고하지 않는다.
- Figma 와이어프레임의 색·크기를 코드 값으로 쓰지 않는다. 값은 Pencil 변수(= Figma 디자인 시스템 변수)만 쓴다.
- Pencil → Figma 내보내기 도구를 만들거나 다시 들이지 않는다(`figma:build`는 2026-10-27 제거, 사용자 결정).

## 보고 형식

```
Pencil 대조 보고
- 노드: <이름> (<id>) / 스크린샷: 확인 · 미확인
- Figma 참고: <Wxx 또는 디자인 시스템 컴포넌트> / 없음
- 토큰: 추가 요청 <목록> · 가장 가까운 토큰으로 대체 <목록>
- 검증: tokens:check <결과>, typecheck <결과>
- 미대조 항목: <목록 또는 없음>
```
