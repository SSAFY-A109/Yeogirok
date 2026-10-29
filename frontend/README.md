# 여기록 FE

다른 사람의 발자취를 따라 여행하고, 나의 발자취는 또 다른 사람의 여행이 되는 여행 코스 공유 서비스 **여기록**의 React Native(Expo) 클라이언트입니다.

- 스택: Expo SDK 58 · React Native 0.88 · TypeScript · Expo Router · pnpm
- 기능 범위와 우선순위: [`docs/frontend-spec.md`](docs/frontend-spec.md) (원천: 팀 Notion 기능 명세서 v2)
- API 연결 규칙: [`docs/api-guide.md`](docs/api-guide.md) — 개발은 MSW, 배포는 실제 백엔드. 명세는 팀 Notion·Swagger를 확인합니다.
- 디자인: 팀 Figma `자율 A109` → Pencil(`design.pen`) → [`design/DESIGN.md`](design/DESIGN.md)

## 시작하기

```bash
pnpm install
pnpm start          # Expo 개발 서버
```

- 아직 Expo 기본 템플릿 의존성만 설치되어 있습니다. NativeWind·RNR·TanStack Query 등 나머지 스택과 테스트 도구 설치 순서는 [`docs/setup.md`](docs/setup.md)에 있습니다.
- 지도·AR·카메라 같은 네이티브 모듈은 Expo Go에서 동작하지 않으므로 개발 빌드(`expo-dev-client`)로 실행합니다.

## 폴더

| 위치 | 내용 |
| --- | --- |
| `app/` | Expo Router 라우트(화면 조립만) |
| `features/<domain>/` | 도메인 코드 — `api/`, `components/`, `model.ts`, `errors.ts` |
| `components/ui/` | 공용 UI(React Native Reusables 기반) |
| `lib/`, `hooks/`, `api/` | 순수 유틸·공용 훅·HTTP 클라이언트와 MSW 목 |
| `design/`, `design.pen` | 디자인 토큰·의도·Pencil 작업 파일 |
| `docs/` | 기능 명세 요약·API 가이드·설치 순서·하네스 설명 |
| `.agents/`, `.claude/`, `AGENTS.md` | AI 코딩 에이전트 규칙(하네스) |
| `.maestro/` | E2E 테스트(Maestro) |

## 명령

```bash
pnpm start | android | ios | web   # 실행
pnpm lint                          # ESLint (expo lint)
pnpm typecheck                     # tsc --noEmit
pnpm tokens:sync                   # design.pen → design/tokens.json → tailwind.config.js · global.css · lib/theme.ts
pnpm tokens:check                  # 토큰 일치·산출물·UI 코드 토큰 위반 검사
pnpm harness:check                 # 하네스 무결성 검사 (통과 시 한 줄)
pnpm e2e                           # Maestro E2E (MAESTRO_APP_ID=<번들 id> pnpm e2e)
```

## AI 코딩 에이전트(하네스)

Claude Code 등 에이전트는 이 저장소 루트에서 시작합니다. 진입점은 [`AGENTS.md`](AGENTS.md)이고, 하네스 구성·규칙 요약은 [`docs/harness.md`](docs/harness.md)에 있습니다. 하네스 원본은 `dfizae/reactNativeHaness`의 `version-2` 브랜치입니다.
