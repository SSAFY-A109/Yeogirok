# 설치 순서

`create-expo-app` 기본 템플릿(SDK 58)으로 시작한 상태에서, `AGENTS.md`가 전제하는 스택을 들이는 순서다. 버전은 2026-10-27 npm 기준 참고값이고, 설치할 때 그 시점의 공식 문서로 다시 확인한다(Expo는 SDK마다 바뀐다).

> 에이전트는 MSW 외의 패키지를 설치하기 전에 사용자 확인을 받는다(`AGENTS.md` 작업 범위와 승인). 네이티브 모듈이 들어가면 개발 빌드를 다시 만든다.

## 0. 준비

- Node.js LTS, pnpm(`package.json`의 `packageManager` 버전). `corepack enable`로 맞추거나 `npm i -g pnpm`.
- `pnpm install`

## 1. 개발 빌드

```bash
pnpm expo install expo-dev-client
pnpm expo run:android        # 로컬 빌드 (Android Studio 필요) — 또는 EAS: pnpm dlx eas-cli build --profile development
```

빌드한 앱을 기기에 설치한 뒤에는 `pnpm start`로 JS를 바로 반영한다. 네이티브 모듈·config plugin을 바꿨을 때만 다시 빌드한다(규칙 85).

## 2. 스타일 — NativeWind · React Native Reusables · 폰트

| 패키지 | 참고 버전 | 비고 |
| --- | --- | --- |
| `nativewind` | 4.2.x | NativeWind v4는 Tailwind CSS 3 기준이다 — `tailwindcss`는 3.x로 맞춘다(설치 시 NativeWind 문서로 확인) |
| React Native Reusables | — | `pnpm ui:add https://reactnativereusables.com/r/nativewind/<name>.json` (규칙 70) |
| `@expo-google-fonts/noto-sans-kr` | 0.4.x | Regular·Medium·Bold를 `expo-font`로 등록 |
| `lucide-react-native` + `react-native-svg` | 1.61.x · 15.x | `react-native-svg`는 `pnpm expo install` |

- NativeWind의 babel·metro 설정과 `global.css` import는 NativeWind 문서 순서를 따른다.
- `tailwind.config.js`, `global.css`, `lib/theme.ts`는 이미 `pnpm tokens:sync`로 생성되어 있다. 직접 편집하지 않는다.

## 3. 데이터 — 서버 상태·전역 상태·HTTP·보안 저장소

```bash
pnpm add @tanstack/react-query zustand axios
pnpm expo install expo-secure-store
```

`api/client.ts`·`api/envelope.ts`·`api/errors.ts`는 `docs/api-guide.md` §3 목표 형태로 만든다.

- `EXPO_PUBLIC_API_URL`에는 호스트만 지정한다(`/api/v1` 제외). 개발은 MSW 요청용 HTTPS 주소, 배포는 실제 백엔드 HTTPS 주소를 설정한다.
- 개발 환경에서는 아래 MSW 구성을 함께 적용한다. 배포 환경에서는 MSW를 불러오거나 시작하지 않는다.

## 4. 테스트 — jest-expo · Testing Library · MSW

```bash
pnpm expo install jest-expo jest
pnpm add -D @testing-library/react-native msw@2 react-native-url-polyfill fast-text-encoding
```

- `package.json`에 `"test": "jest"`, jest preset `jest-expo`를 추가한다.
- MSW는 **v2**로 고정한다(v3은 React Native용 `msw/native`가 없다). 앱 목·jest 목 구성은 `docs/api-guide.md` §9.
- 개발은 전체 API 핸들러를 라우터 시작 전에 준비한다. 서비스 API의 미처리 요청은 오류로 처리한다. 서버 없는 Maestro E2E도 `__DEV__ === true`인 개발 빌드를 사용한다.
- Maestro CLI는 npm 패키지가 아니다 — https://maestro.dev 에서 각자 설치한다(`.maestro/README.md`).

## 5. 확인

```bash
pnpm typecheck && pnpm lint && pnpm tokens:check && pnpm harness:check
```

## 결정 전이라 아직 설치하지 않는 것

- 지도 SDK, AR 라이브러리(규칙 85, 명세 DEC-08·14)
- 위치·카메라 모듈(`expo-location`, `expo-camera`, `expo-image-picker`)은 해당 화면을 만들 때 승인 후 설치한다
