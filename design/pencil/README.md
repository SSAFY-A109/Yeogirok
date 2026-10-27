# Pencil 대안 드래프트 (`design/pencil/`)

**메인 작업 파일은 리포 루트의 `design.pen`이며, 이 파일이 코드 구현의 확정 원천이다.** 에이전트는 `design.pen`이 에디터에 열려 있을 때만 Pencil MCP를 쓸 수 있다.

이 디렉토리에는 메인 파일과 분리해 탐색하는 대안 드래프트(`<flow>-<screen>.pen`)만 둔다. 대안이 확정되면 `design.pen`으로 옮기고, 여기 파일은 히스토리로 남긴다.

## 확정 흐름

```
Figma 자율 A109 › Design 페이지 (팀 디자인 시스템: 변수·텍스트 스타일·컴포넌트 — 값의 출처)
Figma 자율 A109 › Wireframe 페이지 (팀 와이어프레임 Wxx — 화면 구성·문구·흐름 참고)
   │  (에이전트가 읽어서 옮긴다. 코드는 Figma를 읽지 않는다)
   ▼
design.pen (Pencil: 변수 49개 + 아트보드) ── 확정 원천
   ├─ sync-pen-tokens.cjs ──▶ design/tokens.json ──sync-tokens.cjs──▶ tailwind.config.js · global.css · lib/theme.ts
   └─ 구현 전 대조 (MCP Get · TakeScreenshot, design-map.json의 노드 id) ──▶ app/ features/ components/
```

- 토큰 변경 순서는 Figma 값 확인 → Pencil 변수(`SetVariables`) → `pnpm tokens:sync`다. `tokens:check`가 `design.pen`과 `tokens.json`의 불일치를 잡는다.
- Figma와 Pencil의 이름 대응은 `design/DESIGN.md` §2 표에 있다.

## 규약

- 아트보드 이름은 `Wxx 화면명 · 상태`를 쓴다(예: `W09 여행방 상세`, `W09 여행방 상세 · 오류`). 번호는 Figma Wireframe 페이지의 화면 번호와 같게 한다. 대안 아트보드는 `· 대안 1`을 붙인다.
- 라우트와의 연결은 `design/design-map.json`의 `screens` 항목에서 `pencil` 필드로 한다.
- 색·반경·크기·서체는 `design.pen` 문서 변수(`$primary`, `$radius-md`, `$size-button-lg`, `$font-sans` …)만 참조한다. 변수 이름이 곧 `tokens.json` 경로다.
- 컴포넌트 이름은 `design/DESIGN.md` 5장의 인벤토리와 같게 짓는다.

## 에이전트 작업 절차

1. Pencil 에디터에서 루트의 `design.pen`을 연다.
2. `mcp__pencil__get_app_state` → `mcp__pencil__read_skill` 순서로 호출한다.
3. `.agents/skills/pencil-design/SKILL.md`의 절차를 따른다(A 드래프트 · B 대조 · C 토큰 동기화).
4. 시안이 확정되면 `design-map.json`에 노드 id를 기록한다.

`design.pen`은 평문 JSON이다. 에이전트는 MCP로 읽고, 파일을 직접 읽는 것은 스크립트(`scripts/sync-pen-tokens.cjs`, `scripts/check-harness.cjs`)뿐이다.
